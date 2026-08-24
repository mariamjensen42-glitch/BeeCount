#!/usr/bin/env python3
"""训练 BeeCount 的端侧交易分类模型（TF Lite）。

输入：导出的历史交易 CSV，列含 counterparty,note,categoryName
      （从 BeeCount「设置 → 导出 CSV」，或自行从数据库导出）。
输出：app/src/main/assets/ml/ 下的
      - category_model.tflite   推理模型
      - vocab.txt                字符级词表（每行一个 token）
      - labels.txt               类别名列表（与模型输出维度一一对应）

用法：
    python scripts/train_category_classifier.py --csv export.csv \
        --out app/src/main/assets/ml --max-len 32 --epochs 12

依赖：tensorflow>=2.12, pandas, sklearn
"""
import argparse
import os
import numpy as np
import pandas as pd
from sklearn.preprocessing import LabelEncoder
import tensorflow as tf
from tensorflow.keras import layers, models, utils


MAX_LEN = 32
VOCAB_SIZE = 5000  # 字符词表上限（含未知字 0）
EMBED_DIM = 32
TRAIN_SPLIT = 0.85


def load_data(csv_path: str, max_len: int):
    df = pd.read_csv(csv_path)
    df = df[["counterparty", "note", "categoryName"]].fillna("")
    df["text"] = (df["counterparty"].astype(str) + " " + df["note"].astype(str)).str.strip()
    df = df[df["text"].str.len() > 0]
    df = df[df["categoryName"].str.len() > 0]

    # 字符级词表
    counter = {}
    for t in df["text"]:
        for ch in t[:max_len]:
            counter[ch] = counter.get(ch, 0) + 1
    vocab = {ch: i + 1 for i, (ch, _) in enumerate(
        sorted(counter.items(), key=lambda kv: kv[1], reverse=True)[: VOCAB_SIZE - 1])}

    def tokenize(s: str) -> list[int]:
        return [vocab.get(ch, 0) for ch in s[:max_len]]

    x = np.array([tokenize(t) for t in df["text"]], dtype=np.int32)
    le = LabelEncoder()
    y = le.fit_transform(df["categoryName"].to_numpy())
    y_cat = utils.to_categorical(y, num_classes=len(le.classes_))

    perm = np.random.permutation(len(x))
    cut = int(len(x) * TRAIN_SPLIT)
    return (x[perm[:cut]], y_cat[perm[:cut]], x[perm[cut:]], y_cat[perm[cut:]],
            vocab, le.classes_.tolist())


def build_model(vocab_size: int, num_classes: int, max_len: int) -> models.Model:
    inp = layers.Input(shape=(max_len,), dtype="int32")
    x = layers.Embedding(vocab_size, EMBED_DIM, mask_zero=True)(inp)
    x = layers.Conv1D(64, 3, activation="relu")(x)
    x = layers.GlobalMaxPooling1D()(x)
    x = layers.Dense(64, activation="relu")(x)
    out = layers.Dense(num_classes, activation="softmax")(x)
    model = models.Model(inp, out)
    model.compile(optimizer="adam", loss="categorical_crossentropy", metrics=["accuracy"])
    return model


def export_tflite(model: models.Model, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()
    with open(os.path.join(out_dir, "category_model.tflite"), "wb") as f:
        f.write(tflite)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", required=True, help="历史交易 CSV 路径")
    ap.add_argument("--out", default="app/src/main/assets/ml", help="模型输出目录")
    ap.add_argument("--max-len", type=int, default=MAX_LEN)
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--batch", type=int, default=64)
    args = ap.parse_args()

    x_train, y_train, x_val, y_val, vocab, classes = load_data(args.csv, args.max_len)
    print(f"样本 {len(x_train) + len(x_val)} 条，类别 {len(classes)} 个，词表 {len(vocab)} 字")

    model = build_model(VOCAB_SIZE, len(classes), args.max_len)
    model.fit(x_train, y_train, validation_data=(x_val, y_val),
              epochs=args.epochs, batch_size=args.batch, verbose=2)

    export_tflite(model, args.out)
    with open(os.path.join(args.out, "vocab.txt"), "w", encoding="utf-8") as f:
        for ch, _ in sorted(vocab.items(), key=lambda kv: kv[1]):
            f.write(ch + "\n")
    with open(os.path.join(args.out, "labels.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(classes) + "\n")
    print(f"已导出：{args.out}/category_model.tflite, vocab.txt, labels.txt")


if __name__ == "__main__":
    main()
