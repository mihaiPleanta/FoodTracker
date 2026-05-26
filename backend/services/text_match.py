from __future__ import annotations


def stem(word: str) -> str:
    """Naive plural / language-variant trimmer: banana / banane / bananes / banana's -> 'banan'.
    Stops at 4 chars to avoid over-stemming short words."""
    w = word.lower().strip().rstrip("'s")
    while len(w) > 4 and w[-1] in "aeios":
        w = w[:-1]
    return w


def relevance(name: str, categories: list[str], query: str) -> tuple[int, int]:
    """Lower tuple wins.
    Order: category match (strongest signal of category fit) > exact name > name prefix >
    name substring > token-subset > token-overlap > other. Ties broken by shorter name."""
    n = name.lower().strip()
    q = query.lower().strip()
    q_stem = stem(q)
    n_stem = stem(n)
    cat_stems = [stem(c.replace("-", " ").split()[-1]) for c in categories if c]

    n_tokens = set(n.split())
    q_tokens = {t for t in q.split() if t}

    if q_stem and q_stem in cat_stems:
        bucket = 0  # the product is literally in the query's category (e.g., "bananas")
    elif n == q:
        bucket = 1
    elif n_stem.startswith(q_stem) or n.startswith(q):
        bucket = 2
    elif q_stem in n_stem or q in n:
        bucket = 3
    elif q_tokens and q_tokens.issubset(n_tokens):
        bucket = 4
    elif n_tokens & q_tokens:
        bucket = 5
    else:
        bucket = 6
    return (bucket, len(name))
