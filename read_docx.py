import zipfile
import xml.etree.ElementTree as ET
import sys
import os

def extract_text_from_docx(docx_path):
    try:
        with zipfile.ZipFile(docx_path) as z:
            xml_content = z.read('word/document.xml')
            tree = ET.fromstring(xml_content)
            ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
            paragraphs = []
            for p in tree.findall('.//w:p', ns):
                texts = [node.text for node in p.findall('.//w:t', ns) if node.text]
                if texts:
                    paragraphs.append(''.join(texts))
            return '\n'.join(paragraphs)
    except Exception as e:
        return f"Error reading {docx_path}: {e}"

if __name__ == "__main__":
    folder = "d:\\西大校园社交平台\\login-interface-package\\西大拾光"
    out_file = "d:\\西大校园社交平台\\login-interface-package\\docx_contents.txt"
    with open(out_file, "w", encoding="utf-8") as f_out:
        for f in os.listdir(folder):
            if f.endswith('.docx') and not f.startswith('~'):
                f_out.write(f"=== {f} ===\n")
                f_out.write(extract_text_from_docx(os.path.join(folder, f)))
                f_out.write("\n\n")