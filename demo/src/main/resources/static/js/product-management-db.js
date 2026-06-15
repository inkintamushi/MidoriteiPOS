(function() {
  let categories = [];
  let products = [];
  let selectedCategory = null;
  let selectedProduct = null;

  async function json(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(url);
    return response.json();
  }

  async function loadData() {
    categories = await json("/api/categories");
    products = await json("/api/products");
    renderCategories();
    populateCategorySelect();
  }

  function renderCategories() {
    const grid = document.querySelector("#screen-list .grid");
    grid.innerHTML = "";
    categories.forEach(category => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = category.name;
      button.onclick = () => goCategory(category.code);
      grid.appendChild(button);
    });
    const add = document.createElement("button");
    add.className = "btn";
    add.type = "button";
    add.textContent = "カテゴリー追加";
    add.onclick = goAddCategory;
    grid.appendChild(add);
  }

  function renderProducts() {
    const grid = document.querySelector("#screen-category .grid");
    grid.innerHTML = "";
    products.filter(product => product.category === selectedCategory).forEach(product => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = `${product.name}${product.sold_out ? "（品切れ）" : ""}`;
      button.onclick = () => openConfirmEdit(product.id);
      grid.appendChild(button);
    });
    const add = document.createElement("button");
    add.className = "btn";
    add.type = "button";
    add.textContent = "商品追加";
    add.onclick = goAddProduct;
    grid.appendChild(add);
  }

  function populateCategorySelect() {
    const select = document.getElementById("new-category");
    if (!select) return;
    select.innerHTML = "";
    categories.forEach(category => {
      const option = document.createElement("option");
      option.value = category.code;
      option.textContent = category.name;
      select.appendChild(option);
    });
  }

  window.goCategory = function(categoryCode) {
    selectedCategory = categoryCode || categories[0]?.code;
    renderProducts();
    showScreen("screen-category");
  };

  window.openConfirmEdit = function(productId) {
    selectedProduct = products.find(product => Number(product.id) === Number(productId));
    if (!selectedProduct) return;
    document.getElementById("edit-name").textContent = "商品名：" + selectedProduct.name;
    document.getElementById("radio-soldout").checked = false;
    document.getElementById("radio-delete").checked = false;
    document.getElementById("price-input").value = "";
    showOverlay("overlay-confirm");
  };

  window.executeEdit = async function() {
    if (!selectedProduct) return;
    const soldout = document.getElementById("radio-soldout").checked;
    const del = document.getElementById("radio-delete").checked;
    const price = document.getElementById("price-input").value.trim();

    if (del) {
      await fetch(`/api/admin/products/${selectedProduct.id}`, { method: "DELETE" });
    } else if (soldout) {
      await json(`/api/admin/products/${selectedProduct.id}/sold-out`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ soldOut: true })
      });
    } else if (price) {
      await json(`/api/admin/products/${selectedProduct.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ price: Number(price) })
      });
    } else {
      return;
    }

    document.getElementById("done-name").textContent = "商品名：" + selectedProduct.name;
    document.getElementById("done-line1").textContent = del ? "商品削除" : soldout ? "商品売切" : `変更後：${price}円`;
    document.getElementById("done-line2").textContent = "を完了いたしました。";
    await loadData();
    renderProducts();
    showOverlay("overlay-done");
  };

  window.executeAdd = async function() {
    const name = document.getElementById("new-name").value.trim();
    const price = Number(document.getElementById("new-price").value.trim());
    const category = document.getElementById("new-category").value;
    if (!name || !price || !category) return;
    await json("/api/admin/products", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, price, category })
    });
    document.getElementById("add-done-name").textContent = "商品名：" + name;
    document.getElementById("add-done-price").textContent = "商品価格：" + price + "円";
    document.getElementById("add-done-category").textContent = "カテゴリー：" + categories.find(c => c.code === category)?.name;
    document.getElementById("add-done-nomihoudai").textContent = "";
    await loadData();
    showOverlay("overlay-add-done");
  };

  window.executeAddCategory = async function() {
    const name = document.getElementById("new-category-name").value.trim();
    if (!name) return;
    await json("/api/admin/categories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name })
    });
    document.getElementById("category-done-name").textContent = "カテゴリ名：　" + name;
    await loadData();
    showOverlay("overlay-category-add-done");
  };

  document.addEventListener("DOMContentLoaded", loadData);
})();
