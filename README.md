# DownloadManager

A minimal, low-level Android Xposed module that automatically intercepts download links from web browsers and passes them directly to your favorite download manager application (like IDM+, ADM, or 1DM+).

---

## Visual Preview

| Main Interface | Other Interfaces |
| :---: | :---: |
| <img src="https://github.com/paras-moharle/DownloadManager/blob/main/app/src/main/assets/screenshots/main_ui.png?raw=true" width="300"> | <img src="https://github.com/paras-moharle/DownloadManager/blob/main/app/src/main/assets/screenshots/ui.png?raw=true" width="300"> |

---

## How It Works

Instead of letting your browser handle file downloads natively, this module acts as a smart middleman:

1. **The Hook:** It watches Chromium-based browsers (Chrome, Brave, Edge, Kiwi, etc.) at the system level.
2. **The Capture:** The moment you click a download link, `Hook.java` securely grabs the link along with its hidden metadata (`User-Agent`, and `Referer`).
3. **The Scan:** It automatically checks if the link is expiring soon (like AWS or Google Cloud links) or if the site is blacklisted. If the link is safe, it stops the browser from downloading it.
4. **The Hand-off:** `ForwardActivity.kt` wakes up, checks which download managers are installed on your phone, and seamlessly hands the link over to them.

---

## 🛠️ Requirements & Specs

* **Device:** Android 8.0 (API 26) or higher.
* **Environment:** Rooted via **APatch**, **KernelSU**, or **Magisk**.
* **Framework:** **LSPosed** must be installed and active.
* **Supported Browsers:** Standard Chromium builds (Google Chrome, Brave, Microsoft Edge, Vivaldi, Kiwi Browser, etc.).

---

## 🧪 Short Note: The "Vibe Coded" Experiment

> [!NOTE]
> > ****VIBE CODED PROJECT****
> 
> As a B.Tech Artificial Intelligence & Data Science student, I wanted to explore low-level Android architecture, runtime reverse-engineering, and cross-process data management. 
> 
> Because this project was **vibe coded** to help me experiment and close my development gaps, you might run into unexpected bugs depending on your browser version. **Feel free to break things, open issues, or suggest cleaner ways to optimize the code!**
