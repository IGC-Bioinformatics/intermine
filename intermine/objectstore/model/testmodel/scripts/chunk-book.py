import sys, re

reached_start = False
reached_end = False

def is_desc(line):
    return line.startswith("[") or line.endswith("]")

def is_page_break(line):
    return set(line) == set([' ', '*'])

def is_blank(line):
    return len(line) == 0

tests = [is_blank, is_page_break, is_desc]

def not_printable(line):
    for test in tests:
        yield test(line)

for line in sys.stdin:
    line = re.sub("_", '', line.rstrip())

    if reached_start or line.startswith("CHAPTER"):
        reached_start = True

    stripped = line.strip()
    if reached_end or stripped == "THE END":
        reached_end = True

    if reached_start and not reached_end and not any(not_printable(stripped)):
        print(line)

