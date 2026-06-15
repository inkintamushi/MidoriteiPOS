(function() {
  let rows = [];
  let current = null;

  function tableNumber() {
    return Number(new URLSearchParams(location.search).get("table") || localStorage.getItem("currentOrderTable") || 0);
  }

  async function loadHistory() {
    const table = tableNumber();
    const url = table ? `/api/orders/history?tableNumber=${table}` : "/api/orders/history";
    rows = await (await fetch(url)).json();
    const tbody = document.querySelector(".list-table tbody");
    tbody.innerHTML = "";
    rows.forEach(row => {
      const delivered = Number(row.delivered_quantity || 0);
      const ordered = Number(row.qty || 0);
      const canceled = Number(row.canceled_quantity || 0);
      const activeQty = ordered - canceled;
      const isDelivered = delivered >= activeQty && activeQty > 0;
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${row.name}</td>
        <td>${activeQty}</td>
        <td>${delivered}</td>
        <td><button class="edit-btn ${isDelivered ? "green" : "orange"}" type="button" onclick="openEditByItem(${row.item_id})">${isDelivered ? "配膳済" : "未配膳"}</button></td>
      `;
      tbody.appendChild(tr);
    });
  }

  window.openEditByItem = function(itemId) {
    current = rows.find(row => Number(row.item_id) === Number(itemId));
    if (!current) return;
    document.getElementById("overlay-edit").classList.add("active");
  };

  window.openHaizen = function() {
    if (!current) return;
    document.getElementById("haizen-table").textContent = "卓番号：" + current.table_number;
    document.getElementById("haizen-item").textContent = "商品名：" + current.name;
    document.getElementById("haizen-qty-input").value = current.delivered_quantity || 0;
    document.getElementById("haizen-qty-input").max = current.delivered_quantity || 0;
    document.getElementById("haizen-time").textContent = "注文時刻：" + (current.created_at || "");
    document.getElementById("overlay-edit").classList.remove("active");
    document.getElementById("overlay-haizen-confirm").classList.add("active");
  };

  window.openCancel = function() {
    if (!current) return;
    const cancelable = Number(current.qty || 0) - Number(current.delivered_quantity || 0) - Number(current.canceled_quantity || 0);
    document.getElementById("cancel-table").textContent = "卓番号：" + current.table_number;
    document.getElementById("cancel-item").textContent = "商品名：" + current.name;
    document.getElementById("cancel-qty-input").value = Math.max(0, cancelable);
    document.getElementById("cancel-qty-input").max = Math.max(0, cancelable);
    document.getElementById("cancel-time").textContent = "注文時刻：" + (current.created_at || "");
    document.getElementById("overlay-edit").classList.remove("active");
    document.getElementById("overlay-cancel-confirm").classList.add("active");
  };

  window.openHaizenDone = async function() {
    if (!current) return;
    await fetch(`/api/staff/order-items/${current.item_id}/undeliver`, { method: "PUT" });
    document.getElementById("haizen-done-table").textContent = "卓番号：" + current.table_number;
    document.getElementById("haizen-done-item").textContent = "商品名：" + current.name;
    document.getElementById("haizen-done-qty").textContent = "数量：" + (current.delivered_quantity || 0);
    document.getElementById("overlay-haizen-confirm").classList.remove("active");
    document.getElementById("overlay-haizen-done").classList.add("active");
    await loadHistory();
  };

  window.openCancelDone = async function() {
    if (!current) return;
    await fetch(`/api/staff/order-items/${current.item_id}/cancel`, { method: "PUT" });
    document.getElementById("cancel-done-table").textContent = "卓番号：" + current.table_number;
    document.getElementById("cancel-done-item").textContent = "商品名：" + current.name;
    document.getElementById("cancel-done-qty").textContent = "数量：" + document.getElementById("cancel-qty-input").value;
    document.getElementById("overlay-cancel-confirm").classList.remove("active");
    document.getElementById("overlay-cancel-done").classList.add("active");
    await loadHistory();
  };

  document.addEventListener("DOMContentLoaded", loadHistory);
})();
