# -*- coding: utf-8 -*-
"""把 miuix_deps 下已下载的 AAR/jar 统一解出 classes.jar 汇入 cp/。

配合 fetch_compose_deps.py 使用：那个脚本负责按 POM 递归**下载**，
本脚本负责把落盘的制品**归一化**到 classpath 目录（cp/<artifact>.jar）。

分开的原因：递归解析遇到 KMP 依赖环或个别坐标 404 时会中断，
但已下载的 AAR 都是有效的——重跑归一化即可复用，不必重新下载。

同时做两件防御：
1. 同名 artifact 版本冲突时保留**较新**的（Compose 生态里 1.12.0 与 1.9.x 混存很常见）。
2. KMP 空壳（classes.jar < 200B）不写入 cp——它只是转发壳，真身在 androidx 侧。
"""
import os
import re
import zipfile

DEPS = r"D:\CEBIANLAN\File\RedGuo\_tools\miuix_deps"
CP = os.path.join(DEPS, "cp")

MIN_CLASSES_JAR = 200          # 小于此视为 KMP 空壳
EMPTY_JAR_KEEP = ("kotlin-stdlib", "annotation", "coroutines")  # 这些本就是 jar形态


def ver_key(name):
    """从artifact-version 里抠出可比较的版本元组。"""
    m = re.search(r"-(\d+)\.(\d+)\.(\d+)", name)
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0)


def read_artifact(path):
    """返回 classes.jar 的字节内容；非AAR 的 jar 直接返回文件内容。"""
    if path.endswith(".aar"):
        try:
            z = zipfile.ZipFile(path)
        except Exception:
            return None
        if "classes.jar" not in z.namelist():
            return None
        return z.read("classes.jar")
    with open(path, "rb") as fh:
        return fh.read()


def main():
    if not os.path.isdir(CP):
        os.makedirs(CP)
    existing = {f for f in os.listdir(CP)}

    added, skipped, replaced = [], [], []
    for f in sorted(os.listdir(DEPS)):
        full = os.path.join(DEPS, f)
        if not os.path.isfile(full):
            continue
        if not (f.endswith(".aar") or f.endswith(".jar")):
            continue
        if f.startswith("kotlin-stdlib") or f.startswith("kotlinx-coroutines"):
            # 编译器目录里已有 2.4.20 的版本，优先用那份
            src_alt = os.path.join(r"D:\CEBIANLAN\File\RedGuo\_tools\kotlin",
                                   "coroutines.jar" if "coroutines" in f else "kotlin-stdlib.jar")
            if os.path.isfile(src_alt) and "kotlin-stdlib" in f:
                data = read_artifact(src_alt)
                tgt = os.path.join(CP, os.path.basename(f))
                with open(tgt, "wb") as out:
                    out.write(data)
                added.append(f + " (来自 _tools/kotlin 2.4.20)")
                continue
        data = read_artifact(full)
        if data is None:
            skipped.append(f + " 无 classes.jar")
            continue
        if len(data) < MIN_CLASSES_JAR and not any(k in f for k in EMPTY_JAR_KEEP):
            skipped.append(f + " 空壳/过小")
            continue

        base = f[:-4] if f.endswith(".aar") else f[:-4]
        # 去掉 -version 后缀，得到纯 artifact 名
        artifact = re.sub(r"-\d+\.\d+.*$", "", base)
        tgt_name = artifact + ".jar"
        tgt = os.path.join(CP, tgt_name)

        if tgt_name in existing:
            if os.path.getsize(tgt) >= len(data):
                replaced.append(tgt_name + " 保留已有较大版本")
                continue
        with open(tgt, "wb") as out:
            out.write(data)
        added.append(tgt_name)
        existing.add(tgt_name)

    print("新增/更新 %d 个:" % len(added))
    for a in added:
        print("   +", a)
    if replaced:
        print("跳过（已有更大版本）:")
        for r in replaced:
            print("   =", r)
    if skipped:
        print("跳过:")
        for s in skipped:
            print("   -", s)
    print("\ncp jar 总数:", len(os.listdir(CP)))


if __name__ == "__main__":
    main()