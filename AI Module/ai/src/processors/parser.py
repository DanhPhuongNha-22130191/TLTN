import re
from typing import List, Dict

try:
    from underthesea import sent_tokenize
except ImportError:
    sent_tokenize = None


class SentenceSegmenter:
    def split(self, text: str) -> List[str]:
        """Splits Vietnamese text into list of sentences using underthesea or a fallback regex."""
        if sent_tokenize is not None:
            return [s.strip() for s in sent_tokenize(text) if s.strip()]

        # Fallback: split on punctuation marks, keep Vietnamese sentence endings.
        parts = re.split(r'(?<=[\.\!\?]|[\.\!\?]"|[\.\!\?]”|[\.\!\?]’)', text)
        return [p.strip() for p in parts if p.strip()]


class MarkdownParser:
    HEADER_PATTERN = re.compile(r'^(#{1,6})\s+(.*)', re.MULTILINE)

    def extract_sections(self, text: str) -> List[Dict]:
        """Extracts sections from a markdown document based on headings."""
        sections = []
        matches = list(self.HEADER_PATTERN.finditer(text))

        if not matches:
            return [{"header": "", "content": text}]

        for i, match in enumerate(matches):
            level = len(match.group(1))
            title = match.group(2).strip()

            start = match.end()
            end = matches[i + 1].start() if i + 1 < len(matches) else len(text)

            content = text[start:end].strip()
            sections.append({
                "level": level,
                "header": title,
                "content": content
            })

        return sections
