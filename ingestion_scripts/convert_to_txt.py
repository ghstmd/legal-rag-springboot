import os
import win32com.client
from docx import Document

INPUT_DIR = "embed/tvpl_downloads"
DOCX_DIR = "embed/output_docx"
TXT_DIR = "embed/output_txt"

os.makedirs(DOCX_DIR, exist_ok=True)
os.makedirs(TXT_DIR, exist_ok=True)

def doc_to_docx(doc_path, docx_path, word):
    doc = word.Documents.Open(doc_path)
    doc.SaveAs(docx_path, FileFormat=16)  # 16 = wdFormatXMLDocument (.docx)
    doc.Close()

def docx_to_txt(docx_path, txt_path):
    document = Document(docx_path)
    with open(txt_path, "w", encoding="utf-8") as f:
        for para in document.paragraphs:
            text = para.text.strip()
            if text:
                f.write(text + "\n")

def main():
    word = win32com.client.Dispatch("Word.Application")
    word.Visible = False

    for filename in os.listdir(INPUT_DIR):
        if not filename.lower().endswith(".doc"):
            continue

        doc_path = os.path.abspath(os.path.join(INPUT_DIR, filename))
        base_name = os.path.splitext(filename)[0]

        docx_path = os.path.abspath(os.path.join(DOCX_DIR, base_name + ".docx"))
        txt_path = os.path.abspath(os.path.join(TXT_DIR, base_name + ".txt"))

        print(f"🔄 Processing: {filename}")

        try:
            doc_to_docx(doc_path, docx_path, word)
            docx_to_txt(docx_path, txt_path)
        except Exception as e:
            print(f"❌ Error with {filename}: {e}")

    word.Quit()
    print("✅ Done!")

if __name__ == "__main__":
    main()
