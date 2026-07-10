(function() {
  function tableNumber() {
    return Number(new URLSearchParams(location.search).get("table") || localStorage.getItem("currentOrderTable") || 0);
  }

  document.addEventListener("DOMContentLoaded", async () => {
    const table = tableNumber();
    const url = table ? `/api/orders/history?tableNumber=${table}` : "/api/orders/history";
    const rows = await (await fetch(url)).json();
    const body = document.getElementById("history-body");
    const empty = document.getElementById("history-empty");
    const total = document.getElementById("history-total");
    body.innerHTML = "";
    let grandTotal = 0;

    let shownRows = 0;
    rows.forEach(item => {
      const qty = Number(item.qty || 0) - Number(item.canceled_quantity || 0);
      if (qty <= 0) return;
      shownRows++;
      const lineTotal = qty * Number(item.unit_price || 0);
      grandTotal += lineTotal;
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${item.name}</td>
        <td>${qty}</td>
        <td>${lineTotal.toLocaleString()}</td>
      `;
      body.appendChild(tr);
    });

    empty.style.display = shownRows ? "none" : "block";
    total.textContent = `合計：${grandTotal.toLocaleString()}円`;
    document.getElementById("order-nav").onclick = () => {
      location.href = table ? `/order?table=${table}` : "/order";
    };
  });
})();
