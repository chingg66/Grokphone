const features = {
  dashboard: {
    number: "01", name: "OVERVIEW", title: "重要的事，<br>一眼就知道。",
    description: "CPU 各核頻率與負載波形、電池、網路、記憶體和儲存狀態，集中在同一個清晰視野裡。",
    bullets: ["即時 CPU 負載曲線", "電池與網路摘要", "CPU / GPU 效能評估與桌面小工具"],
    label: "CPU / LIVE", icon: "dashboard"
  },
  hardware: {
    number: "02", name: "HARDWARE", title: "不只看型號，<br>也看此刻表現。",
    description: "從 SoC 廠商、市場名稱到每個 CPU 集群的即時頻率，連 GPU、顯示面板、HDR 與刷新率都完整呈現。",
    bullets: ["處理器品牌與核心組態", "GPU 與顯示面板", "各集群即時頻率"],
    label: "SOC / CLUSTERS", icon: "cpu"
  },
  system: {
    number: "03", name: "SYSTEM", title: "裝置的身分，<br>完整攤開。",
    description: "型號、主板、SKU、基頻與建構指紋，搭配 Android 版本、ROM、核心和安全補丁資訊。",
    bullets: ["裝置與 SKU 識別", "Android / ROM 版本", "核心與安全補丁"],
    label: "ANDROID / BUILD", icon: "system"
  },
  battery: {
    number: "04", name: "BATTERY", title: "電量之外，<br>更懂電池狀態。",
    description: "用正圓儀表呈現電量，持續查看溫度、電壓、瞬時電流、功率與健康度，也能開啟電池懸浮窗。",
    bullets: ["即時溫度與功率", "健康度與電荷量", "可選電池懸浮窗"],
    label: "POWER / HEALTH", icon: "battery"
  },
  network: {
    number: "05", name: "NETWORK", title: "連得好不好，<br>用數據說話。",
    description: "查看 IPv4 / IPv6、Wi-Fi 頻段與訊號，再透過下載測速了解首字節延遲、即時、平均與峰值速率。",
    bullets: ["Wi-Fi 鏈路與訊號", "下載速率實測", "延遲 / 平均 / 峰值"],
    label: "NETWORK / SPEED", icon: "network"
  },
  apps: {
    number: "06", name: "APPLICATIONS", title: "你的應用列表，<br>由你決定分享。",
    description: "取得明示同意後，才顯示應用數量與列表。點開任一應用即可查看 ABI、原生庫、元件與權限，架構一目了然。",
    bullets: ["明示同意後才讀取", "ABI / 原生庫一覽", "使用者 / 系統分類"],
    label: "APPS / ABI", icon: "apps"
  },
  camera: {
    number: "07", name: "CAMERA", title: "每顆鏡頭，<br>都有自己的履歷。",
    description: "逐一檢視 Camera2 裝置的像素、解析度、光圈、焦距、感光元件、變焦範圍、幀率與閃光燈。",
    bullets: ["精確像素與解析度", "光圈、焦距與變焦", "即時幀率檢測"],
    label: "CAMERA2 / OPTICS", icon: "camera"
  },
  sensor: {
    number: "08", name: "SENSORS", title: "感覺不到的，<br>也能即時看見。",
    description: "列出裝置全部可用感測器，展開即可查看三軸即時讀數、單位、量程、解析度與功耗。",
    bullets: ["三軸即時數值", "量程與解析度", "感測器功耗資訊"],
    label: "SENSORS / LIVE", icon: "sensor"
  }
};

const tabs = [...document.querySelectorAll("[data-feature]")];
const panel = document.querySelector("#feature-panel");
let selectedFeature = "dashboard";
let featureTimer;

function selectFeature(key, moveFocus = false) {
  if (!features[key]) return;
  selectedFeature = key;
  const data = features[key];
  tabs.forEach((tab) => {
    const selected = tab.dataset.feature === key;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
    if (selected && moveFocus) tab.focus();
  });
  clearTimeout(featureTimer);
  panel.classList.add("switching");
  featureTimer = setTimeout(() => {
    panel.setAttribute("aria-labelledby", `tab-${key}`);
    document.querySelector("#feature-kicker").textContent = `${data.name} / ${data.number}`;
    document.querySelector("#feature-title").innerHTML = data.title;
    document.querySelector("#feature-description").textContent = data.description;
    const list = document.querySelector("#feature-list");
    list.replaceChildren(...data.bullets.map((text) => {
      const item = document.createElement("li");
      item.textContent = text;
      return item;
    }));
    document.querySelector("#instrument-label").textContent = data.label;
    document.querySelector("#instrument-number").textContent = `${data.number} — 08`;
    document.querySelector("#instrument-icon use").setAttribute("href", `#i-${data.icon}`);
    panel.classList.remove("switching");
  }, 140);
}

tabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectFeature(tab.dataset.feature));
  tab.addEventListener("keydown", (event) => {
    let next;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") next = (index + 1) % tabs.length;
    if (event.key === "ArrowLeft" || event.key === "ArrowUp") next = (index - 1 + tabs.length) % tabs.length;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = tabs.length - 1;
    if (next !== undefined) {
      event.preventDefault();
      selectFeature(tabs[next].dataset.feature, true);
      tabs[next].scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
    }
  });
});

const appearanceImage = document.querySelector("#appearance-image");
const modeButtons = [...document.querySelectorAll("[data-mode]")];
const swatches = [...document.querySelectorAll("[data-accent]")];
const modes = {
  dark: { file: "assets/screenshot-dark.png", alt: "機懂 深色模式預覽", label: "DARK" },
  light: { file: "assets/screenshot-light.png", alt: "機懂 淺色模式預覽", label: "LIGHT" },
  dynamic: { file: "assets/screenshot-dynamic.png", alt: "機懂 動態取色預覽", label: "DYNAMIC" }
};
let selectedMode = "dark";
let selectedAccent = "鼠尾草綠";

function updateAppearance() {
  const mode = modes[selectedMode];
  appearanceImage.src = mode.file;
  appearanceImage.alt = mode.alt;
  document.querySelector("#preview-label").textContent = `${mode.label} / ${selectedAccent}`;
}

modeButtons.forEach((button) => {
  button.addEventListener("click", () => {
    selectedMode = button.dataset.mode;
    modeButtons.forEach((item) => {
      const active = item === button;
      item.classList.toggle("active", active);
      item.setAttribute("aria-pressed", String(active));
    });
    updateAppearance();
  });
});

function hexToRgb(hex) {
  const value = hex.replace("#", "");
  return [0, 2, 4].map((start) => parseInt(value.slice(start, start + 2), 16));
}

swatches.forEach((button) => {
  button.addEventListener("click", () => {
    const accent = button.dataset.accent;
    selectedAccent = button.dataset.name;
    const [r, g, b] = hexToRgb(accent);
    document.documentElement.style.setProperty("--accent", accent);
    document.documentElement.style.setProperty("--accent-soft", `rgba(${r}, ${g}, ${b}, .13)`);
    document.querySelector(".appearance-stage").style.backgroundColor = accent;
    document.querySelector("#accent-name").textContent = selectedAccent;
    swatches.forEach((item) => {
      const active = item === button;
      item.classList.toggle("active", active);
      item.setAttribute("aria-pressed", String(active));
    });
    updateAppearance();
  });
});

const gallery = document.querySelector("#gallery");
function moveGallery(direction) {
  const shot = gallery.querySelector(".shot");
  const gap = parseFloat(getComputedStyle(gallery).columnGap) || 18;
  gallery.scrollBy({ left: direction * (shot.getBoundingClientRect().width + gap), behavior: "smooth" });
}
document.querySelector("#gallery-prev").addEventListener("click", () => moveGallery(-1));
document.querySelector("#gallery-next").addEventListener("click", () => moveGallery(1));

const dialog = document.querySelector("#image-dialog");
const dialogImage = document.querySelector("#dialog-image");
document.querySelectorAll(".shot").forEach((shot) => {
  shot.addEventListener("click", () => {
    dialogImage.src = shot.dataset.full;
    dialogImage.alt = shot.dataset.alt;
    dialog.showModal();
  });
});
document.querySelector(".dialog-close").addEventListener("click", () => dialog.close());
dialog.addEventListener("click", (event) => {
  const rect = dialog.getBoundingClientRect();
  const outside = event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom;
  if (outside) dialog.close();
});

const toast = document.querySelector("#toast");
let toastTimer;
document.querySelectorAll('a[download]').forEach((link) => {
  link.addEventListener("click", () => {
    clearTimeout(toastTimer);
    toast.textContent = "已開始下載 機懂 v1.14.0";
    toast.classList.add("visible");
    toastTimer = setTimeout(() => toast.classList.remove("visible"), 3200);
  });
});
