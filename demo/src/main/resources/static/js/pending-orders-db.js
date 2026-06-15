(function() {
  let current = null;
  let rows = [];

  async function loadPending() {
    rows = await (await fetch("/api/staff/pending-orders")).json();
    const tbody = document.getElementById("order-list");
    tbody.innerHTML = "";
    rows.forEach(row => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${row.table_no}</td>
        <td>${row.item}</td>
        <td>${row.remaining_qty}</td>
        <td><button class="deliver-btn" type="button" onclick="openConfirm(${row.id})">配膳</button></td>
      `;
      tbody.appendChild(tr);
    });
  }

  window.openConfirm = function(id) {
    current = rows.find(row => Number(row.id) === Number(id));
    if (!current) return;
    document.getElementById("confirm-table").textContent = "卓番号：" + current.table_no;
    document.getElementById("confirm-item").textContent = "商品名：" + current.item;
    document.getElementById("confirm-qty").value = current.remaining_qty;
    document.getElementById("confirm-qty").max = current.remaining_qty;
    showOverlay("overlay-confirm");
  };

  window.executeDeliver = async function() {
    if (!current) return;
    await fetch(`/api/staff/order-items/${current.id}/deliver`, { method: "PUT" });
    document.getElementById("done-table").textContent = "卓番号：" + current.table_no;
    document.getElementById("done-item").textContent = "商品名：" + current.item;
    document.getElementById("done-qty").textContent = "個数：" + current.remaining_qty;
    showOverlay("overlay-done");
  };

  window.finishDeliver = function() {
    closeAllOverlays();
    current = null;
    loadPending();
  };

  document.addEventListener("DOMContentLoaded", loadPending);
})();
