(function() {
  let products = [];
  let categories = [];
  let selectedProduct = null;
  let pageTotal = 0;

  function tableNumber() {
    const fromUrl = new URLSearchParams(location.search).get("table");
    if (fromUrl) {
      localStorage.setItem("currentOrderTable", fromUrl);
      return Number(fromUrl);
    }
    return Number(localStorage.getItem("currentOrderTable") || 0);
  }

  async function loadJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(url);
    return response.json();
  }

  function renderCategories() {
    const bar = document.querySelector(".category-bar");
    if (!bar) return;
    bar.innerHTML = '<button class="tab active" data-filter="all">すべて</button>';
    categories.forEach(category => {
      const button = document.createElement("button");
      button.className = "tab";
      button.type = "button";
      button.dataset.filter = category.code;
      button.textContent = category.name;
      bar.appendChild(button);
    });

    bar.querySelectorAll(".tab").forEach(tab => {
      tab.addEventListener("click", () => {
        bar.querySelectorAll(".tab").forEach(t => t.classList.remove("active"));
        tab.classList.add("active");
        const filter = tab.dataset.filter;
        document.querySelectorAll(".card").forEach(card => {
          card.style.display = filter === "all" || card.dataset.category === filter ? "grid" : "none";
        });
      });
    });
  }

  function renderProducts() {
    const list = document.querySelector(".product-list");
    if (!list) return;
    list.innerHTML = "";

    products.forEach(product => {
      const card = document.createElement("div");
      card.className = "card";
      card.dataset.category = product.category;
      card.dataset.price = product.price;
      card.dataset.productId = product.id;
      card.innerHTML = `
        <div class="image">
          <img src="${product.image_path}" alt="${product.name}" style="width:100%;height:100%;object-fit:cover;border-radius:15px;">
        </div>
        <div class="name">${product.name}</div>
        <div class="price">${Number(product.price).toLocaleString()}円</div>
        <button class="order-btn" type="button" ${product.sold_out ? "disabled" : ""}>${product.sold_out ? "品切れ" : "注文"}</button>
      `;
      card.querySelector(".order-btn").onclick = () => openOrderModal(product);
      list.appendChild(card);
    });
  }

  function openOrderModal(product) {
    selectedProduct = product;
    const modal = document.getElementById("modal");
    const qty = document.getElementById("qty");
    document.getElementById("modal-name").innerText = product.name;
    document.getElementById("modal-img").src = product.image_path;
    document.getElementById("modal-img").alt = product.name;
    qty.value = 1;
    modal.classList.add("active");
  }

  async function submitOrder() {
    if (!selectedProduct) return;
    const table = tableNumber();
    if (!table) {
      alert("卓番号が未選択です。QRまたは卓選択から開いてください。");
      return;
    }
    const qtyInput = document.getElementById("qty");
    const qty = Math.max(1, Number(qtyInput.value || 1));
    const result = await loadJson("/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        tableNumber: table,
        items: [{ productId: selectedProduct.id, quantity: qty }]
      })
    });

    pageTotal += Number(result.totalPrice || 0);
    document.getElementById("total-area").innerText = `合計：${pageTotal.toLocaleString()}円`;
    document.getElementById("modal").classList.remove("active");
    document.getElementById("complete-name").innerText = selectedProduct.name;
    document.getElementById("complete-qty").innerText = `個数：${qty}`;
    document.getElementById("complete-img").src = selectedProduct.image_path;
    document.getElementById("complete-img").alt = selectedProduct.name;
    document.getElementById("complete-modal").classList.add("active");
  }

  async function checkoutCall() {
    const table = tableNumber();
    const result = await loadJson("/api/orders/checkout-call", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tableNumber: table })
    });
    document.getElementById("payment-total").innerText = `合計：${Number(result.totalPrice || pageTotal).toLocaleString()}円`;
  }

  async function callStaff() {
    const table = tableNumber();
    await loadJson("/api/orders/call-staff", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tableNumber: table })
    });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    categories = await loadJson("/api/categories");
    products = await loadJson("/api/products");
    renderCategories();
    renderProducts();

    const table = tableNumber();
    const tableLabel = document.getElementById("table-number");
    if (tableLabel) tableLabel.textContent = table ? `卓番号：${table}` : "卓番号：未選択";

    document.getElementById("yes").onclick = submitOrder;
    document.getElementById("payment-yes").onclick = async () => {
      document.getElementById("payment-modal").classList.remove("active");
      await checkoutCall();
      document.getElementById("payment-complete-modal").classList.add("active");
    };
    document.getElementById("staff-call-yes").onclick = async () => {
      document.getElementById("staff-call-modal").classList.remove("active");
      await callStaff();
      document.getElementById("staff-call-complete-modal").classList.add("active");
    };
    document.getElementById("history-yes").onclick = () => {
      location.href = "/order_history";
    };
  });
})();
