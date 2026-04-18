from collections import Counter


SOURCE = "**17**.,."
OUTPUT_PATH = "combinations.txt"


def unique_permutations(counter, prefix, remaining, results):
    if remaining == 0:
        results.append(prefix)
        return

    for char in sorted(counter):
        if counter[char] == 0:
            continue
        counter[char] -= 1
        unique_permutations(counter, prefix + char, remaining - 1, results)
        counter[char] += 1


def main():
    counter = Counter(SOURCE)
    results = []
    unique_permutations(counter, "", len(SOURCE), results)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write(f"原始字符串: {SOURCE}\n")
        f.write(f"唯一组合总数: {len(results)}\n\n")
        for item in results:
            f.write(item + "\n")

    print(f"唯一组合总数: {len(results)}")
    print(f"结果已写入: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
