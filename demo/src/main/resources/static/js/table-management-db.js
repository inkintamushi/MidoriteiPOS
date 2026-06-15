(function() {
  const statusToDb = {
    "なし": "EMPTY",
    "対応中": "IN_USE",
    "要対応": "NEEDS_HELP",
    "未対応": "NEEDS_HELP",
    "要応援": "NEEDS_HELP",
    "清掃応援": "CLEANING_HELP",
    "清掃中": "CLEANING",
    "使用中止": "STOPPED"
  };

  const dbToButton = {
    EMPTY: { text: "なし", cls: "light" },
    IN_USE: { text: "対応中", cls: "green" },
    NEEDS_HELP: { text: "要対応", cls: "orange" },
    CLEANING_HELP: { text: "清掃応援", cls: "orange" },
    CLEANING: { text: "清掃中", cls: "orange" },
    STOPPED: { text: "使用中止", cls: "gray" }
  };

  async function loadTables() {
    const response = await fetch("/api/staff/tables");
    if (!response.ok) return;
    const tables = await response.json();
    tables.forEach(table => updateTableButton(table.table_number, table.status));
  }

  function updateTableButton(tableNumber, status) {
    const state = dbToButton[status];
    if (!state) return;
    document.querySelectorAll("#screen-table tbody tr").forEach(tr => {
      if (tr.cells[0]?.textContent == tableNumber) {
        const btn = tr.cells[2]?.querySelector("button");
        if (btn) {
          btn.textContent = state.text;
          btn.className = "btn " + state.cls;
        }
      }
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

  if (typeof window.execAnnai === "function") {
    const originalExecAnnai = window.execAnnai;
    window.execAnnai = async function() {
      const checked = [...document.querySelectorAll('input[name="taku"]:checked')].map(c => c.value);
      const guestCount = parseInt(document.getElementById("ninzu")?.value, 10);
      if (checked.length && Number.isInteger(guestCount) && guestCount > 0) {
        for (const tableNo of checked) {
          await updateTable(tableNo, { guestCount, status: "IN_USE" });
        }
      }
      originalExecAnnai();
    };
  }

  document.addEventListener("DOMContentLoaded", loadTables);
})();
