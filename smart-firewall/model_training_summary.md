# Smart Home Firewall — Model Training Summary
## Decentralized Smart Home Firewall with Federated Learning — Anomaly Detection Component

---

## 1. Starting Point (Phase 1 — Progress Presentation)

**Setup:** Isolation Forest trained on benign traffic only, from the CICIDS2017 dataset.
**Result:**
- Overall accuracy: 77%
- Benign traffic correctly identified: 85%
- Attack traffic detected: 46%

**Known issue:** `contamination=0.15` was an arbitrary guess, not tuned to the data.

---

## 2. Dataset

**Source:** CICIDS2017 (`chethuhn/network-intrusion-dataset` on Kaggle), industry-standard labeled network intrusion dataset.

**Composition (after cleaning):**
- Total rows: 2,827,876
- Benign: 2,271,320 (80.3%)
- Attack: 556,556 (19.7%)

**Class breakdown (attacks):**
| Attack type | Count |
|---|---|
| DoS Hulk | 230,124 |
| PortScan | 158,804 |
| DDoS | 128,025 |
| DoS GoldenEye | 10,293 |
| FTP-Patator | 7,935 |
| SSH-Patator | 5,897 |
| DoS slowloris | 5,796 |
| DoS Slowhttptest | 5,499 |
| Bot | 1,956 |
| Web Attack – Brute Force | 1,507 |
| Web Attack – XSS | 652 |
| Infiltration | 36 |
| Web Attack – SQL Injection | 21 |
| Heartbleed | 11 |

**Known limitation:** Significant class imbalance, both overall (80/20 benign/attack) and within attack types (some classes have fewer than 40 samples total — statistically too small to evaluate reliably on their own).

---

## 3. Step 1 — Hyperparameter Tuning

**Change:** Grid search over `n_estimators`, `max_samples`, `max_features`, and `contamination` (using the true validation-set attack ratio instead of a guess), optimizing for F1 score.

**Best config found:** `contamination=0.2, max_features=1.0, max_samples=1.0, n_estimators=200`

**Result on test set:**
| Metric | Phase 1 | After tuning |
|---|---|---|
| Overall accuracy | 77% | 79% |
| Benign recall | 85% | 80% |
| **Attack recall** | **46%** | **78%** |
| Attack precision | — | 49% |
| Attack F1 | — | 0.60 |

**Takeaway:** Proper hyperparameter tuning alone nearly doubled attack detection.

---

## 4. Step 2 — Feature Engineering

**Change:** Added 10 extra raw CICIDS2017 columns (packet length stats, header lengths, Down/Up ratio) plus 4 derived ratio features:
- `fwd_bwd_pkt_ratio` — forward/backward packet count ratio
- `fwd_bwd_bytes_ratio` — forward/backward byte volume ratio
- `syn_ack_ratio` — SYN vs ACK flag ratio (surfaces SYN floods)
- `flow_iat_cv` — coefficient of variation of inter-arrival time (surfaces automated/bursty traffic)

Total features used: 38 (up from 24).

**Result on test set:**
| Metric | After tuning | + Feature engineering |
|---|---|---|
| Overall accuracy | 79% | 82% |
| Benign recall | 80% | 80% |
| **Attack recall** | **78%** | **87%** |
| Attack precision | 49% | 52% |
| Attack F1 | 0.60 | 0.65 |

**Takeaway:** Better-shaped input features pushed attack recall further without sacrificing benign recall — a genuine improvement, not just a different trade-off point.

---

## 5. Step 3 — Three-Layer Detection Pipeline

**Motivation:** Addressing "what happens when an attack is misclassified as normal" — a single unsupervised model has an inherent recall ceiling. Built a layered architecture instead of relying on one model:

**Layer 1 — Widened Isolation Forest:** Threshold deliberately chosen (via validation sweep) to target 95% recall rather than using the default cutoff, trading precision for fewer missed attacks.
- Result: 95.9% recall, 35.0% precision

**Layer 2 — Rule-based signatures:** Independent of any ML model, thresholds derived from percentiles of the benign training data itself (not hardcoded):
- SYN flood rule (high SYN count, zero ACKs)
- Port scan rule (near-instant flow, ≤3 packets, no response)
- High-rate flood rule (packets/sec far beyond benign baseline)

**Combination:** Layer 1 OR Layer 2 → maximizes recall, since a flow only needs one layer to catch it.

**Combined Layer 1+2 result on test set:**
| Metric | Value |
|---|---|
| Attack recall | 98% |
| Attack precision | 34% |
| Overall accuracy | 62% |

**Layer 3 — Random Forest confirmation:** Trained on full labeled data, applied only to flagged traffic to add confidence — does not filter/dismiss anything back to "Normal," only re-tiers flagged items into `Suspicious` vs `Confirmed Attack`.

**Final tier breakdown (test set, 111,312 real attacks / 454,264 real benign):**
| Tier | Real attacks | False alarms |
|---|---|---|
| Confirmed Attack | 108,672 | 5,677 |
| Suspicious | 412 | 207,917 |
| Normal (missed) | 2,228 | 240,670 |

**Headline results:**
- **98% of attacks flagged by at least one layer**
- **95% precision within the "Confirmed Attack" tier** (108,672 / 114,349) — i.e., alerts you'd actually act on are highly trustworthy
- **Only 2.0% of attacks missed entirely** by all three layers

---

## 6. Open Questions / Next Steps Identified

1. **Overfitting risk:** Current train/test split is random across rows. CICIDS2017 flows from the same attack burst can be near-identical, so random splitting may let the model "see" near-duplicates of test attacks during training. **Recommended fix (not yet done):** re-split by day — train on Monday–Thursday, test only on Friday's attacks (DDoS, PortScan) to check whether performance holds on genuinely unseen attack sessions.
2. **Class imbalance:** Rare attack types (Heartbleed: 11, SQL Injection: 21, Infiltration: 36) have too few samples for reliable individual evaluation. Currently mitigated only via `class_weight="balanced"` on the Random Forest; Isolation Forest is unaffected by this since it trains on benign data only.
3. **Duplicate-row check:** Not yet performed — worth verifying CICIDS2017 doesn't contain exact/near-exact duplicate flows inflating reported metrics.
4. **Leakage check:** Confirmed current feature set does not include Flow ID, IP addresses, or timestamps — reduces risk of the model "memorizing" specific IPs rather than learning traffic behavior.

---

## 7. Architecture Note for Federated Learning (Phase 2)

Isolation Forest and Random Forest don't have "weights" in the neural-network sense, so classic FedAvg (weight averaging) doesn't directly apply. Recommended approach for Phase 2: **federated score/output aggregation** — each simulated node trains its own local Isolation Forest, and outputs (anomaly scores or flags) are combined across nodes, rather than averaging tree structures directly. This preserves the existing architecture while giving an honest, citable answer for a viva question on this point.

---

## Progression Summary Table

| Stage | Attack Recall | Attack Precision | Overall Accuracy |
|---|---|---|---|
| Phase 1 baseline | 46% | — | 77% |
| + Hyperparameter tuning | 78% | 49% | 79% |
| + Feature engineering | 87% | 52% | 82% |
| + Three-layer pipeline (Confirmed tier) | 98%* | 95%** | — |

\* Recall of the combined Layer 1+2 flagging system before tiering
\** Precision specifically within the "Confirmed Attack" tier, after Layer 3 filtering
