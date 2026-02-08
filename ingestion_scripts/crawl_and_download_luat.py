import os
import json
import time
import glob
import io
from datetime import datetime
from PIL import Image
import pytesseract
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import StaleElementReferenceException, ElementClickInterceptedException, NoSuchElementException
from webdriver_manager.chrome import ChromeDriverManager

# --- Configuration ---
download_dir = os.path.abspath("tvpl_downloads")
metadata_file = "tvpl_metadata.json"
os.makedirs(download_dir, exist_ok=True)

# Tesseract path (update this if needed)
# For Windows: Download from https://github.com/UB-Mannheim/tesseract/wiki
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

# Load existing metadata
if os.path.exists(metadata_file):
    with open(metadata_file, "r", encoding="utf-8") as f:
        metadata = json.load(f)
else:
    metadata = []

# Track downloaded URLs and filenames to avoid duplicates
downloaded_urls = {item.get("url") for item in metadata}
downloaded_filenames = {item.get("filename") for item in metadata}

# --- Captcha Handling Functions ---

def check_captcha_exists(driver):
    """Check if captcha is present on the page"""
    try:
        driver.find_element(By.ID, "ctl00_Content_txtSecCode")
        return True
    except NoSuchElementException:
        return False

def solve_captcha_ocr(driver, save_debug_images=False):
    """Try to solve captcha using OCR with multiple preprocessing strategies"""
    try:
        print("    🤖 Attempting OCR captcha solve...")
        
        # Try both URL patterns (case-insensitive)
        captcha_img = None
        for xpath in ['//img[@src="/RegistImage.aspx"]', '//img[@src="/registimage.aspx"]', 
                      '//img[contains(translate(@src, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz"), "registimage")]']:
            try:
                captcha_img = driver.find_element(By.XPATH, xpath)
                break
            except:
                continue
        
        if not captcha_img:
            print("    ❌ Could not find captcha image")
            return False
        
        # Take screenshot of captcha
        captcha_screenshot = captcha_img.screenshot_as_png
        original_image = Image.open(io.BytesIO(captcha_screenshot))
        
        # Try multiple preprocessing strategies
        preprocessing_strategies = [
            ("Standard", lambda img: img.convert('L').point(lambda x: 0 if x < 140 else 255, '1')),
            ("High Contrast", lambda img: img.convert('L').point(lambda x: 0 if x < 120 else 255, '1')),
            ("Low Contrast", lambda img: img.convert('L').point(lambda x: 0 if x < 160 else 255, '1')),
            ("Simple Grayscale", lambda img: img.convert('L')),
        ]
        
        best_result = None
        best_confidence = 0
        
        for strategy_name, preprocess_func in preprocessing_strategies:
            try:
                # Apply preprocessing
                processed_image = preprocess_func(original_image.copy())
                
                # Save debug images if enabled
                if save_debug_images:
                    processed_image.save(f'captcha_debug_{strategy_name.replace(" ", "_")}.png')
                
                # Perform OCR with multiple configs
                ocr_configs = [
                    '--psm 7 -c tessedit_char_whitelist=0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',
                    '--psm 8 -c tessedit_char_whitelist=0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz',
                    '--psm 7',
                ]
                
                for config in ocr_configs:
                    captcha_text = pytesseract.image_to_string(processed_image, config=config).strip()
                    captcha_text = ''.join(captcha_text.split())  # Remove whitespace
                    
                    # Clean up common OCR mistakes
                    captcha_text = captcha_text.replace('O', '0').replace('o', '0')  # O -> 0
                    captcha_text = captcha_text.replace('l', '1').replace('I', '1')  # l/I -> 1
                    captcha_text = captcha_text.replace('Z', '2').replace('z', '2')  # Z -> 2
                    captcha_text = captcha_text.replace('S', '5').replace('s', '5')  # S -> 5
                    captcha_text = captcha_text.replace('B', '8')  # B -> 8
                    captcha_text = captcha_text.upper()
                    
                    # Check if valid length
                    if len(captcha_text) == 6:
                        # Simple confidence: prefer alphanumeric results
                        confidence = sum(c.isalnum() for c in captcha_text)
                        if confidence > best_confidence:
                            best_confidence = confidence
                            best_result = captcha_text
                            print(f"    🔍 {strategy_name}: '{captcha_text}' (confidence: {confidence}/6)")
                
            except Exception as e:
                print(f"    ⚠️  {strategy_name} failed: {e}")
                continue
        
        if not best_result or best_confidence < 6:
            print(f"    ❌ No valid captcha found (best: '{best_result}', confidence: {best_confidence}/6)")
            return False
        
        print(f"    ✨ Best result: '{best_result}'")
        
        # Enter captcha text
        captcha_input = driver.find_element(By.ID, "ctl00_Content_txtSecCode")
        captcha_input.clear()
        captcha_input.send_keys(best_result)
        
        # Find and click submit button (try multiple possible button IDs)
        submit_button = None
        button_ids = [
            "ctl00_Content_CheckButton",      # Download page
            "ctl00_Content_cmdLogin",         # Login/other pages
            "ctl00$Content$CheckButton",      # Alternative naming
            "ctl00$Content$cmdLogin",         # Alternative naming
        ]
        
        for button_id in button_ids:
            try:
                submit_button = driver.find_element(By.ID, button_id)
                print(f"    🔘 Found submit button: {button_id}")
                break
            except:
                continue
        
        # If no button found by ID, try by type and value
        if not submit_button:
            try:
                # Try finding by type="submit" and common Vietnamese button text
                buttons = driver.find_elements(By.XPATH, '//input[@type="submit"]')
                for btn in buttons:
                    btn_value = btn.get_attribute("value")
                    if btn_value and any(keyword in btn_value.lower() for keyword in ['ok', 'xác nhận', 'submit', 'gửi']):
                        submit_button = btn
                        print(f"    🔘 Found submit button by text: {btn_value}")
                        break
            except:
                pass
        
        if not submit_button:
            print("    ❌ Could not find submit button")
            return False
        
        # Click the button
        submit_button.click()
        time.sleep(3)
        
        # Check if captcha is still present (means it failed)
        if check_captcha_exists(driver):
            print("    ❌ OCR failed - captcha still present")
            return False
        else:
            print("    ✅ OCR successfully solved captcha!")
            return True
            
    except Exception as e:
        print(f"    ❌ OCR error: {e}")
        return False

def solve_captcha_manual(driver):
    """Wait for user to solve captcha manually"""
    try:
        print("\n" + "="*60)
        print("🔐 CAPTCHA DETECTED - MANUAL INTERVENTION REQUIRED")
        print("="*60)
        print("👉 Please look at the browser window")
        print("👉 Enter the captcha code in the text field")
        print("👉 Click the 'Ok' or 'Xác nhận' button")
        print("👉 Then press ENTER here to continue...")
        print("="*60)
        
        input("⏸️  Press ENTER after solving captcha...")
        
        # Wait a bit for page to reload
        time.sleep(3)
        
        # Verify captcha is gone
        if check_captcha_exists(driver):
            print("    ⚠️  Captcha still present. Did you solve it?")
            print("    💡 Make sure you:")
            print("       1. Entered the code correctly")
            print("       2. Clicked the submit button (Ok/Xác nhận)")
            print("       3. Waited for page to reload")
            retry = input("Try again? (y/n): ").lower()
            if retry == 'y':
                return solve_captcha_manual(driver)
            return False
        else:
            print("    ✅ Manual captcha solve successful!")
            return True
            
    except Exception as e:
        print(f"    ❌ Manual solve error: {e}")
        return False

def handle_captcha_hybrid(driver, max_ocr_attempts=3):
    """
    Hybrid captcha solver: Try OCR first, fallback to manual
    
    Args:
        driver: Selenium webdriver
        max_ocr_attempts: Number of OCR attempts before falling back to manual
        
    Returns:
        bool: True if captcha solved, False otherwise
    """
    if not check_captcha_exists(driver):
        return True  # No captcha present
    
    print("\n🔐 Captcha detected on page!")
    
    # Try OCR multiple times with different strategies
    for attempt in range(max_ocr_attempts):
        print(f"\n📸 OCR Attempt {attempt + 1}/{max_ocr_attempts}")
        
        # Enable debug images on last OCR attempt
        save_debug = (attempt == max_ocr_attempts - 1)
        
        if solve_captcha_ocr(driver, save_debug_images=save_debug):
            return True
        
        # If failed and not last attempt, refresh to get new captcha
        if attempt < max_ocr_attempts - 1:
            print("    🔄 Refreshing to get new captcha...")
            driver.refresh()
            time.sleep(3)
            
            if not check_captcha_exists(driver):
                print("    ✅ Captcha disappeared after refresh!")
                return True
    
    # All OCR attempts failed, fallback to manual
    print(f"\n⚠️  OCR failed after {max_ocr_attempts} attempts")
    print("💡 Tip: Check captcha_debug_*.png files to see what OCR saw")
    print("🔄 Switching to manual mode...")
    
    return solve_captcha_manual(driver)

# --- Other Helper Functions ---

def save_metadata():
    """Save metadata to file immediately"""
    with open(metadata_file, "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)
    print(f"    💾 Metadata saved ({len(metadata)} documents)")

def check_file_exists(filename):
    """Check if file exists in download directory"""
    if not filename:
        return False
    filepath = os.path.join(download_dir, filename)
    exists = os.path.exists(filepath)
    if exists:
        size = os.path.getsize(filepath)
        print(f"    ✓ File exists: {filename} ({size} bytes)")
    return exists

def is_already_downloaded(doc_url, title):
    """Check if document is already downloaded"""
    if doc_url in downloaded_urls:
        for item in metadata:
            if item.get("url") == doc_url:
                filename = item.get("filename")
                if filename and check_file_exists(filename):
                    return True, filename
                else:
                    print(f"    ⚠️ Metadata exists but file missing: {filename}")
                    return False, None
        return False, None
    return False, None

# --- Setup Chrome Options ---
options = Options()
options.add_argument("--start-maximized")
options.add_argument("--disable-blink-features=AutomationControlled")
options.add_experimental_option("prefs", {
    "download.default_directory": download_dir,
    "download.prompt_for_download": False,
    "download.directory_upgrade": True,
    "safebrowsing.enabled": True,
    "plugins.always_open_pdf_externally": True,
})

# Initialize driver
print("🚀 Initializing Chrome driver...")
driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=options)

# Set download behavior
driver.execute_cdp_cmd("Page.setDownloadBehavior", {
    "behavior": "allow",
    "downloadPath": download_dir
})

# --- Login ---
print("🔐 Logging in...")
driver.get("https://thuvienphapluat.vn")
try:
    WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.ID, "usernameTextBox")))
    driver.find_element(By.ID, "usernameTextBox").send_keys("fiestasenpai@gmail.com")
    driver.find_element(By.ID, "passwordTextBox").send_keys("fiestasenpai@gmail.com")
    driver.find_element(By.ID, "loginButton").click()
    
    # Check for captcha after login
    time.sleep(2)
    if check_captcha_exists(driver):
        print("🔐 Captcha detected during login!")
        if not handle_captcha_hybrid(driver):
            print("❌ Failed to solve login captcha")
            driver.quit()
            exit()
    
    WebDriverWait(driver, 15).until(EC.presence_of_element_located((By.ID, "Support_HyperLink1")))
    print("✅ Login successful")
except Exception as e:
    print(f"❌ Login failed: {e}")
    driver.quit()
    exit()

# --- Determine starting page ---
last_page = 1
if metadata:
    pages = [item.get("page", 1) for item in metadata]
    last_page = max(pages) if pages else 1
    print(f"\n📖 Found existing data:")
    print(f"   - Total documents: {len(metadata)}")
    print(f"   - Last processed page: {last_page}")
    print(f"   - Downloaded URLs: {len(downloaded_urls)}")
    
    missing_files = []
    for item in metadata:
        filename = item.get("filename")
        if filename and not check_file_exists(filename):
            missing_files.append(filename)
    
    if missing_files:
        print(f"\n⚠️  Warning: {len(missing_files)} files in metadata but missing from disk:")
        for mf in missing_files[:5]:
            print(f"      - {mf}")
        if len(missing_files) > 5:
            print(f"      ... and {len(missing_files) - 5} more")
    
    print(f"\n🤔 Resume options:")
    print(f"   1. Continue from page {last_page} (recommended)")
    print(f"   2. Start from page 1 (will skip existing downloads)")
    print(f"   3. Custom page number")
    
    choice = input(f"Enter choice (1/2/3, default=1): ").strip()
    
    if choice == '2':
        last_page = 1
    elif choice == '3':
        override_page = input(f"Enter starting page number: ").strip()
        last_page = int(override_page) if override_page.isdigit() else last_page
    
    print(f"🚀 Starting from page: {last_page}")
else:
    print("📄 No existing data found. Starting fresh from page 1")

# --- Start scraping ---
base_url = "https://thuvienphapluat.vn/page/tim-van-ban.aspx?keyword=&area=0&type=0&status=0&lan=1&org=0&signer=0&match=True&sort=1&bdate=14/12/1945&edate=15/12/2025"
page = last_page
driver.get(f"{base_url}&page={page}")
time.sleep(3)

# Check for captcha on initial page load
if check_captcha_exists(driver):
    print("🔐 Captcha detected on search page!")
    if not handle_captcha_hybrid(driver):
        print("❌ Failed to solve search page captcha")
        driver.quit()
        exit()

total_downloaded = 0
total_skipped = 0
consecutive_empty_pages = 0
MAX_CONSECUTIVE_EMPTY = 3

def get_item_data(item):
    """Extract data from item element safely"""
    try:
        title_elem = item.find_element(By.XPATH, './/p[@class="nqTitle"]/a')
        title = title_elem.text.strip()
        doc_url = title_elem.get_attribute("href")
        
        try:
            ban_hanh = item.find_element(By.XPATH, './/p[contains(text(), "Ban hành:")]').text.replace("Ban hành:", "").strip()
        except:
            ban_hanh = ""
        
        try:
            hieu_luc = item.find_element(By.XPATH, './/p[contains(., "Hiệu lực:")]').text.replace("Hiệu lực:", "").strip()
        except:
            hieu_luc = ""
        
        try:
            tinh_trang = item.find_element(By.XPATH, './/p[contains(., "Tình trạng:")]').text.replace("Tình trạng:", "").strip()
        except:
            tinh_trang = ""
        
        return {
            "title": title,
            "doc_url": doc_url,
            "ban_hanh": ban_hanh,
            "hieu_luc": hieu_luc,
            "tinh_trang": tinh_trang
        }
    except:
        return None

while True:
    print(f"\n{'='*60}")
    print(f"📄 Processing Page {page}")
    print(f"{'='*60}")
    
    try:
        # Check for captcha before processing page
        if check_captcha_exists(driver):
            print("🔐 Captcha detected on listing page!")
            if not handle_captcha_hybrid(driver):
                print("❌ Failed to solve captcha, skipping page")
                page += 1
                driver.get(f"{base_url}&page={page}")
                time.sleep(3)
                continue
        
        # Try to load page items with retry
        items_found = False
        for attempt in range(3):
            try:
                WebDriverWait(driver, 15).until(
                    EC.presence_of_all_elements_located((By.XPATH, '//div[contains(@class, "content-")]'))
                )
                items_found = True
                break
            except:
                if attempt < 2:
                    print(f"    ⚠️ Retry {attempt + 1}/3: Page not loaded, waiting...")
                    time.sleep(5)
                    driver.refresh()
                    time.sleep(3)
                    
                    # Check for captcha after refresh
                    if check_captcha_exists(driver):
                        print("🔐 Captcha appeared after refresh!")
                        if not handle_captcha_hybrid(driver):
                            break
        
        if not items_found:
            print(f"    ⚠️ No items found after 3 attempts")
            consecutive_empty_pages += 1
            
            if consecutive_empty_pages >= MAX_CONSECUTIVE_EMPTY:
                print(f"\n✅ Reached {MAX_CONSECUTIVE_EMPTY} consecutive empty pages. Stopping.")
                break
            else:
                print(f"    ℹ️ Empty page count: {consecutive_empty_pages}/{MAX_CONSECUTIVE_EMPTY}")
                page += 1
                driver.get(f"{base_url}&page={page}")
                time.sleep(3)
                continue
        
        # Get all document items
        items = driver.find_elements(By.XPATH, '//div[contains(@class, "content-")]')
        items_data = []
        
        for item in items:
            data = get_item_data(item)
            if data:
                items_data.append(data)
        
        if not items_data:
            print(f"    ⚠️ No valid items extracted from page")
            consecutive_empty_pages += 1
            
            if consecutive_empty_pages >= MAX_CONSECUTIVE_EMPTY:
                print(f"\n✅ Reached {MAX_CONSECUTIVE_EMPTY} consecutive empty pages. Stopping.")
                break
            else:
                print(f"    ℹ️ Empty page count: {consecutive_empty_pages}/{MAX_CONSECUTIVE_EMPTY}")
                page += 1
                driver.get(f"{base_url}&page={page}")
                time.sleep(3)
                continue
        
        # Reset counter if we found items
        consecutive_empty_pages = 0
        
        print(f"🔗 Found {len(items_data)} items on page {page}")
        
        for idx, data in enumerate(items_data, 1):
            title = data["title"]
            doc_url = data["doc_url"]
            
            # Check if already downloaded
            already_downloaded, existing_filename = is_already_downloaded(doc_url, title)
            
            if already_downloaded:
                print(f"  [{idx}/{len(items_data)}] ⏭️ Already downloaded: {existing_filename[:50]}...")
                total_skipped += 1
                continue
            
            print(f"  [{idx}/{len(items_data)}] 📥 Processing: {title[:60]}...")
            
            try:
                # Navigate to document page
                driver.get(doc_url)
                time.sleep(2)
                
                # Check for captcha on document page
                if check_captcha_exists(driver):
                    print("    🔐 Captcha detected on document page!")
                    if not handle_captcha_hybrid(driver):
                        print("    ❌ Failed to solve captcha, skipping document")
                        driver.get(f"{base_url}&page={page}")
                        time.sleep(2)
                        continue
                
                # Wait for download page to load
                WebDriverWait(driver, 10).until(
                    EC.presence_of_element_located((By.ID, "ctl00_Content_ThongTinVB_vietnameseHyperLink"))
                )
                
                # Get Vietnamese document download link
                vn_download = driver.find_element(By.ID, "ctl00_Content_ThongTinVB_vietnameseHyperLink")
                vn_download_url = vn_download.get_attribute("href")
                
                # Get current files before download
                files_before = set(glob.glob(os.path.join(download_dir, "*")))
                files_before = {f for f in files_before if not f.endswith('.crdownload')}
                
                print(f"    📂 Files before download: {len(files_before)}")
                
                # Trigger download
                driver.execute_script("window.open(arguments[0]);", vn_download_url)
                time.sleep(3)
                
                # Close the new tab
                if len(driver.window_handles) > 1:
                    driver.switch_to.window(driver.window_handles[-1])
                    driver.close()
                    driver.switch_to.window(driver.window_handles[0])
                
                # Wait for download to complete
                print("    ⏳ Waiting for download to complete...")
                download_complete = False
                filename = None
                
                for wait_count in range(40):
                    time.sleep(1)
                    
                    crdownload_files = glob.glob(os.path.join(download_dir, "*.crdownload"))
                    files_after = set(glob.glob(os.path.join(download_dir, "*")))
                    files_after = {f for f in files_after if not f.endswith('.crdownload')}
                    new_files = files_after - files_before
                    
                    if wait_count % 10 == 0 and wait_count > 0:
                        print(f"    🔍 Debug [{wait_count}s]: crdownload={len(crdownload_files)}, new_files={len(new_files)}")
                    
                    if not crdownload_files and new_files:
                        latest_file = list(new_files)[0]
                        filename = os.path.basename(latest_file)
                        download_complete = True
                        print(f"    ✓ File detected: {filename}")
                        break
                
                if download_complete:
                    # Save metadata
                    doc_metadata = {
                        "filename": filename,
                        "title": title,
                        "description": title,
                        "url": doc_url,
                        "ban_hanh": data["ban_hanh"],
                        "hieu_luc": data["hieu_luc"],
                        "tinh_trang": data["tinh_trang"],
                        "ngay_crawl": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                        "page": page
                    }
                    
                    metadata.append(doc_metadata)
                    downloaded_urls.add(doc_url)
                    downloaded_filenames.add(filename)
                    save_metadata()
                    
                    total_downloaded += 1
                    print(f"    ✅ Downloaded: {filename}")
                else:
                    print(f"    ⚠️ Download timeout")
                
                # Return to listing page
                driver.get(f"{base_url}&page={page}")
                time.sleep(2)
                
                # Check for captcha after returning
                if check_captcha_exists(driver):
                    print("    🔐 Captcha detected after download!")
                    if not handle_captcha_hybrid(driver):
                        print("    ⚠️ Failed to solve captcha")
                
            except Exception as e:
                print(f"    ❌ Download failed: {e}")
                try:
                    driver.get(f"{base_url}&page={page}")
                    time.sleep(2)
                except:
                    pass
                continue
        
        # Move to next page
        page += 1
        driver.get(f"{base_url}&page={page}")
        time.sleep(3)
    
    except Exception as e:
        print(f"❌ Error on page {page}: {e}")
        consecutive_empty_pages += 1
        if consecutive_empty_pages >= MAX_CONSECUTIVE_EMPTY:
            print(f"\n✅ Too many consecutive errors. Stopping.")
            break
        page += 1
        driver.get(f"{base_url}&page={page}")
        time.sleep(3)
        continue

# --- Summary ---
print(f"\n{'='*60}")
print(f"🎉 Scraping Complete!")
print(f"{'='*60}")
print(f"📥 Total downloaded this session: {total_downloaded}")
print(f"⏭️ Total skipped this session: {total_skipped}")
print(f"📚 Total documents in metadata: {len(metadata)}")
print(f"📄 Last page processed: {page}")
print(f"📁 Files saved to: {download_dir}")
print(f"📋 Metadata saved to: {metadata_file}")
print(f"{'='*60}")

driver.quit()