def pick_recent(rows, meal, limit=20):
    """Pick recently logged foods for the quick-add list.

    `rows` are food-log rows ordered newest first. Recipe entries (non-null
    `ingredients`) are skipped. Foods whose newest log at `meal` exists come
    first, ordered by that log's recency; the rest follow by overall recency.
    Each returned row is the one whose grams/snapshot the client should reuse.
    """
    latest_any = {}
    latest_meal = {}
    for row in rows:
        if row.ingredients is not None:
            continue
        latest_any.setdefault(row.barcode, row)
        if row.meal == meal:
            latest_meal.setdefault(row.barcode, row)
    # dicts keep insertion order, and rows arrive newest first.
    current = list(latest_meal.values())
    others = [row for barcode, row in latest_any.items() if barcode not in latest_meal]
    return (current + others)[:limit]
