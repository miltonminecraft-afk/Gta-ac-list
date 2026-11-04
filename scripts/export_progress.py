"""Publish an allowlisted progress snapshot; never export chat IDs or evidence paths."""
from pathlib import Path
import argparse
import json
import re

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = re.compile(r'(?i)(?:[A-Z]:[/\\]|file://|plugin://|(?:ghp|github_pat)_[a-z0-9_]+|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})')

def select(value, fields):
    return {name: value[name] for name in fields}

def public_snapshot(source, history):
    result = select(source, ['schema', 'updatedAt', 'updatedLabel', 'scope', 'deltaLabel', 'updates', 'backlog'])
    result['updates'] = [select(u, ['finished', 'title', 'detail']) for u in source['updates']]
    result['backlog'] = [select(b, ['title', 'status', 'detail']) for b in source['backlog']]
    result['tasks'] = []
    for task in source['tasks']:
        item = select(task, ['id', 'title', 'owner', 'status', 'current', 'next'])
        item['checks'] = [select(c, ['label', 'state']) for c in task['checks']]
        result['tasks'].append(item)
    result['history'] = []
    for h in history[-48:]:
        item = select(h, ['updatedAt', 'deltaLabel'])
        item['updates'] = [select(u, ['finished', 'title', 'detail']) for u in h['updates']]
        result['history'].append(item)
    if not result['history'] or result['history'][-1]['updatedAt'] != source['updatedAt']:
        result['history'].append(select(result, ['updatedAt', 'deltaLabel', 'updates']))
        result['history'] = result['history'][-48:]
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
    if FORBIDDEN.search(encoded):
        raise ValueError('Private path, identifier, credential pattern or email in public text. Review it before publication.')
    if len(encoded.encode()) > 1024 * 1024:
        raise ValueError('Feed too large')
    return encoded

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--history', type=Path, required=True)
    parser.add_argument('--bundle', action='store_true', help='Also refresh the APK fallback snapshot before building')
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding='utf-8'))
    history = [json.loads(line) for line in args.history.read_text(encoding='utf-8').splitlines() if line.strip()]
    encoded = public_snapshot(source, history)
    destination = ROOT / 'data/status.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(encoded, encoding='utf-8')
    if args.bundle:
        (ROOT / 'app/src/main/assets/snapshot.json').write_text(encoded, encoding='utf-8')
    print(json.dumps({'publicFeed': str(destination), 'tasks': len(source['tasks']), 'snapshot': source['updatedAt']}))

if __name__ == '__main__':
    main()
