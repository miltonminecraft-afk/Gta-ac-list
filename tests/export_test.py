import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from export_progress import public_snapshot

class ExportTest(unittest.TestCase):
    def test_private_evidence_is_excluded(self):
        source=json.loads((Path(__file__).resolve().parents[1]/'data/status.json').read_text(encoding='utf-8'))
        source['tasks'][0]['source']='H:/private/chat-evidence.json'
        source['threadId']='do not publish'
        exported=json.loads(public_snapshot(source, []))
        self.assertNotIn('threadId',exported)
        self.assertNotIn('source',exported['tasks'][0])
        self.assertNotIn('H:/private',json.dumps(exported))
    def test_private_text_blocks_publication(self):
        source=json.loads((Path(__file__).resolve().parents[1]/'data/status.json').read_text(encoding='utf-8'))
        source['tasks'][0]['current']='Read C:/Users/private/file.txt'
        with self.assertRaises(ValueError): public_snapshot(source, [])

if __name__=='__main__': unittest.main()
