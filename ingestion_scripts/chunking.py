"""
Unified Legal Document Processor v2
------------------------------------
Xử lý folder TXT files → 1 file JSON duy nhất với chunks
Format output: chunk_id, source_file, url, token_length, chunk_content
Hỗ trợ append vào JSON có sẵn (incremental update)

Usage:
    python unified_legal_document_processor_v2.py
"""

import re
import os
import json
import tiktoken
from pathlib import Path
from typing import List, Dict, Any
from datetime import datetime

# ============================================================================
# CONFIGURATION
# ============================================================================

INPUT_FOLDER = "embed/output_txt"
OUTPUT_JSON = "embed/all_500_new_chunk.json"
MODEL_NAME = "gpt-4o"
MAX_TOKEN = 800

# ============================================================================
# PART 1: PARSING TXT → JSON TREE
# ============================================================================

def parse_legal_document_to_dict(file_path):
    """Parse single TXT file to dictionary structure"""
    
    with open(file_path, 'r', encoding='utf-8') as f:
        original_text = f.read()
    lines = [line.strip() for line in original_text.splitlines() if line.strip()]

    # Regex patterns for hierarchy detection
    REGEX_LEVEL_MAP = [
        (r'^(phần)\s+(thứ\s+)?((?:[IVXLCDM]+)|(?:[0-9]{1,2}))\b', 1, re.IGNORECASE),
        (r'^(phần)\s+thứ\s+((?:nhất|một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|mười một|mười hai|mười ba|mười bốn|mười lăm|mười sáu|mười bảy|mười tám|mười chín|hai mươi|ba mươi|bốn mươi|năm mươi|sáu mươi|bảy mươi|tám mươi|chín mươi|trăm))\b', 1, re.IGNORECASE),
        (r'^(chương)\s+[IVXLCDM0-9]+', 2, re.IGNORECASE),
        (r'^(mục)\s+(thứ\s+)?((?:[IVXLCDM]+)|(?:[0-9]{1,2}))\b', 3, re.IGNORECASE),
        (r'^(tiểu\s+mục)\s+(thứ\s+)?((?:[IVXLCDM]+)|(?:[0-9]{1,2}))\b', 4, re.IGNORECASE),
        (r'^(điều)\s+[0-9]+', 5, re.IGNORECASE),
        (r'^(khoản)\s+[0-9]+', 6, re.IGNORECASE),
        (r'^(tiểu khoản)\s+[0-9]+', 7, re.IGNORECASE),
        (r'^(?!(I|II|III)[\.\)])([A-ZĐÊÔÔÁÀẢẠÃẦẤẬ])[\.\)]', 10, 0),
        (r'^(I{1,3}|IV|V|VI|VII|VIII|IX|X)[\.\)]', 11, 0),
        (r'^[0-9]+[\.\)]', 12, re.IGNORECASE),
        (r'^(?!(ii|iii)[\.\)])([a-zđêôêáàảạãầấậ])[\.\)]', 13, 0),
        (r'^(ii|iii|iv|v|vi|vii|viii|ix|x)[\.\)]', 14, 0),
    ]

    compiled_regex_levels = []
    for pattern, level, flag in REGEX_LEVEL_MAP:
        if flag:
            compiled_regex_levels.append((re.compile(pattern, flag), level))
        else:
            compiled_regex_levels.append((re.compile(pattern), level))

    appendix_regex = re.compile(r'^(phụ lục)', re.IGNORECASE)

    doc_keywords = [
        "bộ luật", "chỉ thị", "hiến pháp", "lệnh", "luật", "nghị định",
        "nghị quyết liên tịch", "nghị quyết", "pháp lệnh", "quyết định",
        "thông tư liên tịch", "thông tư"
    ]

    # Find title
    title_found = None
    for i, line in enumerate(lines[:20]):
        if any(kw in line.lower() for kw in doc_keywords):
            if i + 1 < len(lines):
                title_found = f"{line} {lines[i+1]}"
            else:
                title_found = line
            break

    # Remove appendix if exists
    appendix_start_index = None
    for i, line in enumerate(lines):
        if appendix_regex.match(line.lower().strip()):
            appendix_start_index = i
            break

    if appendix_start_index is not None:
        lines = lines[:appendix_start_index]

    # Build tree structure
    stack = []
    root = {"level": -1, "content": "", "children": []}
    non_saved_lines = []

    for line in lines:
        matched = False
        for regex, level in compiled_regex_levels:
            if regex.match(line):
                current_level = level
                new_node = {
                    "level": current_level,
                    "content": line,
                    "children": []
                }
                while stack and stack[-1]["level"] >= current_level:
                    stack.pop()
                parent = stack[-1] if stack else root
                parent["children"].append(new_node)
                stack.append(new_node)
                matched = True
                break

        if not matched:
            if not stack:
                non_saved_lines.append(line)
            else:
                if stack[-1]["content"]:
                    stack[-1]["content"] += " " + line
                else:
                    stack[-1]["content"] = line

    return {
        "metadata": {
            "tên văn bản": title_found if title_found else "Không xác định",
            "file gốc": os.path.basename(file_path),
            "có phụ lục không?": appendix_start_index is not None,
            "nội dung không lưu": non_saved_lines,
            "thời gian xử lý": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        },
        "content": root["children"]
    }

# ============================================================================
# PART 2: CHUNKING WITH TOKEN OPTIMIZATION
# ============================================================================

def count_tokens(text: str, model_name: str = "gpt-4o") -> int:
    """Count tokens in text"""
    try:
        encoding = tiktoken.encoding_for_model(model_name)
    except Exception:
        encoding = tiktoken.get_encoding("cl100k_base")
    return len(encoding.encode(text))

def ensure_punctuation(text: str) -> str:
    """Add punctuation if missing"""
    return text if re.search(r'[.!?。:：]$', text.strip()) else text + '.'

def extract_all_node_texts(node: Dict[str, Any], collector: List[str]) -> None:
    """Recursively extract all node texts"""
    collector.append(node["content"])
    for child in node.get("children", []):
        extract_all_node_texts(child, collector)

def create_chunks_from_tree(tree_data: Dict[str, Any], 
                            source_file_path: str,
                            model_name: str = "gpt-4o",
                            max_token: int = 800) -> List[Dict[str, Any]]:
    """
    Convert tree structure to optimized chunks
    Returns list of chunks (without chunk_id, will be assigned later)
    """
    
    root_title = tree_data['metadata']['tên văn bản']
    root_node = {
        "content": root_title,
        "children": tree_data.get("content", [])
    }

    # Collect all node texts
    all_node_texts_raw: List[str] = []
    extract_all_node_texts(root_node, all_node_texts_raw)
    all_node_texts = [ensure_punctuation(txt) for txt in all_node_texts_raw]
    set_all_node_texts = set(all_node_texts)

    # DFS to create initial chunks
    all_chunks: List[Dict[str, Any]] = []
    current_path: List[str] = []

    def dfs_build(node: Dict[str, Any]):
        text = ensure_punctuation(node["content"])
        current_path.append(text)
        children = node.get("children", [])

        if not children:
            chunk_lines = current_path[:]
            chunk_text = ''.join(chunk_lines)
            token_length = count_tokens(chunk_text, model_name=model_name)
            all_chunks.append({
                "chunk_content": chunk_lines,
                "token_length": token_length
            })
        else:
            for child in children:
                dfs_build(child)

        current_path.pop()

    dfs_build(root_node)

    # Merge chunks with overlap
    final_chunks: List[Dict[str, Any]] = []
    i = 0
    while i < len(all_chunks):
        current = {
            "chunk_content": all_chunks[i]["chunk_content"][:],
            "token_length": all_chunks[i]["token_length"]
        }
        j = i + 1

        while j < len(all_chunks):
            lines_cur = current["chunk_content"]
            lines_nxt = all_chunks[j]["chunk_content"]

            # Find first different line
            found = -1
            for idx_nxt, line in enumerate(lines_nxt):
                if line not in lines_cur:
                    found = idx_nxt
                    break

            if found >= 0:
                merged_lines = lines_cur + lines_nxt[found:]
            else:
                merged_lines = lines_cur + lines_nxt

            merged_lines = [ensure_punctuation(ln) for ln in merged_lines]
            merged_text = ''.join(merged_lines)
            merged_tokens = count_tokens(merged_text, model_name=model_name)

            if merged_tokens <= max_token:
                current["chunk_content"] = merged_lines
                current["token_length"] = merged_tokens
                j += 1
            else:
                break

        final_chunks.append(current)
        i = j

    # Verify no missing nodes
    used_texts: set = set()
    for chunk in final_chunks:
        for line in chunk["chunk_content"]:
            used_texts.add(line)

    missing = set_all_node_texts - used_texts
    if missing:
        raise ValueError(f"Missing {len(missing)} nodes in chunks", missing)

    # Convert to output format with source_file and url placeholder
    output_chunks = []
    for chunk in final_chunks:
        output_chunks.append({
            "source_file": source_file_path,
            "url": "",  # Placeholder, will be filled if needed
            "token_length": chunk["token_length"],
            "chunk_content": ' '.join(chunk["chunk_content"])
        })

    return output_chunks

# ============================================================================
# PART 3: UNIFIED PROCESSING
# ============================================================================

def process_folder_to_unified_json(input_folder: str, 
                                   output_json: str,
                                   model_name: str = "gpt-4o",
                                   max_token: int = 800,
                                   append_mode: bool = True):
    """
    Process all TXT files in folder and create/append to unified JSON
    
    Args:
        input_folder: Path to folder containing TXT files
        output_json: Path to output unified JSON file
        model_name: Model for token counting
        max_token: Maximum tokens per chunk
        append_mode: If True and output file exists, append new chunks
    """
    
    print("=" * 70)
    print("🚀 UNIFIED LEGAL DOCUMENT PROCESSOR V2")
    print("=" * 70)
    print(f"📁 Input folder: {input_folder}")
    print(f"📄 Output file: {output_json}")
    print(f"🔧 Max tokens per chunk: {max_token}")
    print(f"🔄 Append mode: {'YES' if append_mode else 'NO'}")
    print("=" * 70)
    
    # Load existing data if in append mode
    existing_chunks = []
    next_chunk_id = 1
    processed_files = set()
    
    if append_mode and os.path.exists(output_json):
        print(f"\n📖 Loading existing data from {output_json}...")
        try:
            with open(output_json, 'r', encoding='utf-8') as f:
                existing_chunks = json.load(f)
                
                # Get max chunk_id
                if existing_chunks:
                    max_id = max(chunk.get("chunk_id", 0) for chunk in existing_chunks)
                    next_chunk_id = max_id + 1
                    
                    print(f"   ✅ CONFIRMATION: Next chunk_id will be {next_chunk_id}")
                    print(f"   ✅ CONFIRMATION: Will append after chunk_id {max_id}")
                
                # Track processed files
                processed_files = set(chunk.get("source_file") for chunk in existing_chunks)
                
                print(f"   ✓ Loaded {len(existing_chunks)} existing chunks")
                print(f"   ✓ Next chunk ID will start from: {next_chunk_id}")
                print(f"   ✓ Already processed files: {len(processed_files)}")
        except Exception as e:
            print(f"   ⚠️  Error loading existing file: {e}")
            print(f"   ℹ️  Will create new file instead")
            existing_chunks = []
            next_chunk_id = 1
            processed_files = set()
    else:
        print(f"\n📝 Creating new JSON file (no existing data)")
    
    # Find all TXT files
    txt_files = []
    for root, dirs, files in os.walk(input_folder):
        for file in files:
            if file.lower().endswith('.txt'):
                txt_files.append(os.path.join(root, file))
    
    print(f"\n🔍 Found {len(txt_files)} TXT files")
    
    # Filter out already processed files
    new_txt_files = [f for f in txt_files if f not in processed_files]
    skipped_count = len(txt_files) - len(new_txt_files)
    
    if skipped_count > 0:
        print(f"⏭️  Skipping {skipped_count} already processed files")
    
    if not new_txt_files:
        print("\n✅ No new files to process!")
        print(f"📊 Current file has {len(existing_chunks)} chunks")
        if existing_chunks:
            print(f"📊 Last chunk_id: {max(c['chunk_id'] for c in existing_chunks)}")
        return
    
    print(f"📝 Processing {len(new_txt_files)} new files...\n")
    
    # Process each TXT file
    all_new_chunks = []
    error_files = []
    
    for idx, txt_file in enumerate(new_txt_files, 1):
        filename = os.path.basename(txt_file)
        print(f"[{idx}/{len(new_txt_files)}] 🔄 Processing: {filename}")
        
        try:
            # Step 1: Parse TXT to tree
            print(f"   ├─ Parsing structure...")
            tree_data = parse_legal_document_to_dict(txt_file)
            
            # Step 2: Create chunks
            print(f"   ├─ Creating chunks...")
            chunks = create_chunks_from_tree(tree_data, txt_file, model_name, max_token)
            
            all_new_chunks.extend(chunks)
            print(f"   └─ ✅ Created {len(chunks)} chunks")
            
        except Exception as e:
            print(f"   └─ ❌ Error: {e}")
            error_files.append({"file": filename, "error": str(e)})
    
    # Assign chunk IDs to new chunks
    print(f"\n🔢 Assigning chunk IDs starting from {next_chunk_id}...")
    for chunk in all_new_chunks:
        chunk["chunk_id"] = next_chunk_id
        next_chunk_id += 1
    
    # Combine with existing chunks
    final_chunks = existing_chunks + all_new_chunks
    
    # Save directly as array (no metadata wrapper)
    print(f"\n💾 Saving to {output_json}...")
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(final_chunks, f, ensure_ascii=False, indent=2)
    
    # Summary
    total_docs = len(set(c["source_file"] for c in final_chunks))
    
    print("\n" + "=" * 70)
    print("✅ PROCESSING COMPLETE!")
    print("=" * 70)
    
    # FINAL CONFIRMATION
    if existing_chunks:
        last_old_chunk = max(c['chunk_id'] for c in existing_chunks)
        first_new_chunk = min(c['chunk_id'] for c in all_new_chunks) if all_new_chunks else None
        last_new_chunk = max(c['chunk_id'] for c in all_new_chunks) if all_new_chunks else None
        
        print(f"🎯 FINAL CONFIRMATION:")
        print(f"   ✅ Old chunks: 1 → {last_old_chunk}")
        if first_new_chunk:
            print(f"   ✅ New chunks: {first_new_chunk} → {last_new_chunk}")
            print(f"   ✅ Appended correctly: {first_new_chunk} = {last_old_chunk + 1}")
    
    print(f"\n📊 Summary:")
    print(f"   - Total chunks in file: {len(final_chunks)}")
    print(f"   - New chunks added: {len(all_new_chunks)}")
    print(f"   - Total documents: {total_docs}")
    print(f"   - New documents processed: {len(new_txt_files) - len(error_files)}")
    
    if error_files:
        print(f"\n⚠️  Errors encountered: {len(error_files)}")
        for err in error_files[:5]:
            print(f"   - {err['file']}: {err['error'][:60]}...")
        if len(error_files) > 5:
            print(f"   ... and {len(error_files) - 5} more")
    
    # Token statistics
    token_lengths = [c["token_length"] for c in final_chunks]
    avg_tokens = sum(token_lengths) / len(token_lengths) if token_lengths else 0
    max_tokens_used = max(token_lengths) if token_lengths else 0
    
    print(f"\n📈 Token Statistics:")
    print(f"   - Average tokens/chunk: {avg_tokens:.1f}")
    print(f"   - Max tokens used: {max_tokens_used}")
    print(f"   - Utilization: {(avg_tokens/max_token)*100:.1f}%")
    
    print(f"\n📁 Output saved to: {output_json}")
    print(f"💾 File size: {os.path.getsize(output_json) / 1024 / 1024:.2f} MB")
    print("=" * 70)

# ============================================================================
# MAIN EXECUTION
# ============================================================================

if __name__ == "__main__":
    # Run processor
    process_folder_to_unified_json(
        input_folder=INPUT_FOLDER,
        output_json=OUTPUT_JSON,
        model_name=MODEL_NAME,
        max_token=MAX_TOKEN,
        append_mode=True  # Set False to overwrite existing file
    )
    
    print("\n✨ Done! You can now use the unified JSON for embedding/RAG.")