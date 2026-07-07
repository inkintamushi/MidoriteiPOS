(function() {
  const statusToDb = {
    "清掃未対応": "CLEANING_UNHANDLED",
    "呼出未対応": "CALL_UNHANDLED",
    "清掃要応援": "CLEANING_NEEDS_HELP",
    "呼出要応援": "CALL_NEEDS_HELP",
    "清掃対応中": "CLEANING_IN_PROGRESS",
    "呼出対応中": "CALL_IN_PROGRESS",
    "使用可能": "AVAILABLE",
    "使用中止": "OUT_OF_SERVICE",
    "使用中": "OCCUPIED"
  };

  const dbToButton = {
    CLEANING_UNHANDLED: { text: "清掃未対応", cls: "orange" },
    CALL_UNHANDLED: { text: "呼出未対応", cls: "orange" },
    CLEANING_NEEDS_HELP: { text: "清掃要応援", cls: "orange" },
    CALL_NEEDS_HELP: { text: "呼出要応援", cls: "orange" },
    CLEANING_IN_PROGRESS: { text: "清掃対応中", cls: "green" },
    CALL_IN_PROGRESS: { text: "呼出対応中", cls: "green" },
    AVAILABLE: { text: "使用可能", cls: "light" },
    OUT_OF_SERVICE: { text: "使用中止", cls: "gray" },
    OCCUPIED: { text: "使用中", cls: "green" },
    PAYMENT_WAITING: { text: "会計対応待ち", cls: "orange" }
  };

  async function loadTables() {
    const response = await fetch("/api/staff/tables");
    if (!response.ok) return;
    const tables = await response.json();
    renderTableList(tables);
  }

  // taku.html: 卓一覧を丸ごと描画する。注文・注文履歴・QRボタンは
  // 「使用されている卓番号のみに付随する」(機能統合版_ホーム) ため、
  // 客が着席中(guest_count > 0)の卓にのみ表示する。
  function renderTableList(tables) {
    const tbody = document.getElementById("table-list");
    if (!tbody) return;
    tbody.innerHTML = "";
    tables.forEach(table => {
      const state = dbToButton[table.status] || { text: table.status, cls: "gray" };
      const inUse = table.status !== "AVAILABLE" && table.status !== "OUT_OF_SERVICE";
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${table.table_number}</td>
        <td><button class="btn ${state.cls}" onclick="callTable(${table.table_number})">${state.text}</button></td>
        <td>${inUse ? `<button class="btn green" onclick="orderTable(${table.table_number})">注文</button>` : ""}</td>
        <td>${inUse ? `<button class="btn green" onclick="historyTable(${table.table_number})">履歴</button>` : ""}</td>
        <td>${inUse ? `<button class="btn green qr-btn" onclick="reissueQr(${table.table_number})">再発行</button>` : ""}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  async function updateTable(tableNumber, payload) {
    await fetch(`/api/staff/tables/${tableNumber}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  }

  async function issueQr(tableNumber) {
    const response = await fetch(`/api/staff/tables/${tableNumber}/qr`, { method: "POST" });
    if (!response.ok) throw new Error("QR発行に失敗しました。");
    return response.json();
  }

  if (typeof window.reissueQr === "function") {
    window.reissueQr = async function(tableNo) {
      const result = await issueQr(tableNo);
      const qrUrl = result.orderUrl;
      const qrBox = document.getElementById("qr-code-box");
      const text = document.getElementById("qr-modal-text");

      localStorage.setItem("currentOrderTable", tableNo);
      text.textContent = `卓番号：${tableNo} / ${qrUrl}`;
      qrBox.innerHTML = "";

      if (window.QRCode) {
        new QRCode(qrBox, {
          text: qrUrl,
          width: 160,
          height: 160,
          correctLevel: QRCode.CorrectLevel.M
        });
      } else {
        qrBox.textContent = qrUrl;
      }

      document.getElementById("qr-modal").classList.add("active");
    };
  }

  let courses = [];

  async function loadCourses() {
    const response = await fetch("/api/courses");
    if (!response.ok) return;
    courses = await response.json();
  }

  if (typeof window.execAnnai === "function") {
    const originalExecAnnai = window.execAnnai;
    window.execAnnai = async function() {
      const checked = [...document.querySelectorAll('input[name="taku"]:checked')].map(c => c.value);
      const guestCount = parseInt(document.getElementById("ninzu")?.value, 10);
      const planEl = document.querySelector('input[name="plan"]:checked');
      const course = planEl ? courses.find(c => c.name === planEl.value) : null;
      if (checked.length && Number.isInteger(guestCount) && guestCount > 0) {
        for (const tableNo of checked) {
          await updateTable(tableNo, {
            guestCount,
            status: "OCCUPIED",
            courseId: course ? course.id : null
          });
        }
      }
      originalExecAnnai();
    };
  }

  if (typeof window.execIdou === "function") {
    const originalExecIdou = window.execIdou;
    window.execIdou = async function() {
      const from = document.getElementById("idou-from")?.value;
      const to = document.getElementById("idou-to")?.value;
      if (from && to) {
        await fetch("/api/staff/tables/move", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ fromTableNumber: Number(from), toTableNumber: Number(to) })
        });
      }
      originalExecIdou();
    };
  }

  if (typeof window.execKoukan === "function") {
    const originalExecKoukan = window.execKoukan;
    window.execKoukan = async function() {
      const a = document.getElementById("koukan-a")?.value;
      const b = document.getElementById("koukan-b")?.value;
      if (a && b) {
        await fetch("/api/staff/tables/swap", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tableNumberA: Number(a), tableNumberB: Number(b) })
        });
      }
      originalExecKoukan();
    };
  }

  document.addEventListener("DOMContentLoaded", () => {
    loadTables();
    loadCourses();
  });
})();
