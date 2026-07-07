(function() {
  let categories = [];
  let products = [];
  let selectedCategory = null;
  let selectedProduct = null;

  function tableNumber() {
    const fromUrl = new URLSearchParams(location.search).get("table");
    if (fromUrl) {
      localStorage.setItem("currentOrderTable", fromUrl);
      return Number(fromUrl);
    }
    return Number(localStorage.getItem("currentOrderTable") || 0);
  }

  async function json(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(url);
    return response.json();
  }

  async function loadData() {
    categories = await json("/api/categories");
    products = await json("/api/products");
    renderCategories();
  }

  function renderCategories() {
    const grid = document.getElementById("category-grid");
    grid.innerHTML = "";
    categories.forEach(category => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = category.name;
      button.onclick = () => goCategory(category.code);
      grid.appendChild(button);
    });
  }

  function renderProducts() {
    const grid = document.getElementById("product-grid");
    grid.innerHTML = "";
    products.filter(product => product.category === selectedCategory).forEach(product => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = product.name + (product.sold_out ? "（品切れ）" : "");
      button.disabled = Boolean(product.sold_out);
      button.onclick = () => openConfirm(product);
      grid.appendChild(button);
    });
  }

  window.goCategory = function(categoryCode) {
    selectedCategory = categoryCode;
    renderProducts();
    showScreen("screen-yaki");
  };

  window.openConfirm = function(product) {
    selectedProduct = product;
    document.getElementById("confirm-table").textContent = "卓番号：" + tableNumber();
    document.getElementById("confirm-item").textContent = "商品名：" + product.name;
    document.getElementById("qty-input").value = 1;
    showOverlay("overlay-confirm");
  };

  window.closeConfirm = function() {
    closeAllOverlays();
  };

  window.finishOrder = function() {
    closeAllOverlays();
    showScreen("screen-main");
  };

  window.executOrder = async function() {
    if (!selectedProduct) return;
    const table = tableNumber();
    if (!table) {
      alert("卓番号が未選択です。卓選択画面から開いてください。");
      return;
    }
    const qty = Math.max(1, Number(document.getElementById("qty-input").value || 1));
    await json("/api/staff/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        tableNumber: table,
        items: [{ productId: selectedProduct.id, quantity: qty }]
      })
    });

    document.getElementById("done-table").textContent = "卓番号：" + table;
    document.getElementById("done-item").textContent = "商品名：" + selectedProduct.name;
    document.getElementById("done-qty").textContent = "個数：" + qty;
    showOverlay("overlay-done");
  };

  window.openQR = function() {
    reissueQr(tableNumber());
  };

  document.addEventListener("DOMContentLoaded", loadData);
})();
