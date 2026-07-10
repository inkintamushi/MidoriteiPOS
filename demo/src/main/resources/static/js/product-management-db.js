(function() {
  let categories = [];
  let products = [];
  let courses = [];
  let selectedCategory = null;
  let selectedProduct = null;
  let selectedCourse = null;
  let selectedCourseAction = null; // 'add' | 'remove'
  let selectedCourseCategory = null;
  let courseProducts = [];

  function isPlanCategory(code) {
    return code === "nomi";
  }

  async function json(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error(url);
    return response.json();
  }

  async function loadData() {
    categories = await json("/api/categories");
    products = await json("/api/products");
    courses = await json("/api/courses");
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
      button.onclick = isPlanCategory(category.code) ? () => goCourseList() : () => goCategory(category.code);
      grid.appendChild(button);
    });
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
    select.innerHTML = '<option value="" selected disabled>カテゴリーを選択</option>';
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

    document.getElementById("done-name").textContent = "";
    if (del) {
      document.getElementById("done-line1").textContent = `商品名：${selectedProduct.name} を削除しました。`;
    } else if (soldout) {
      document.getElementById("done-line1").textContent = `商品名：${selectedProduct.name} の売り切れ状態を更新しました`;
    } else {
      document.getElementById("done-line1").textContent = `商品名：${selectedProduct.name} を${price}円に更新しました。`;
    }
    document.getElementById("done-line2").textContent = "";
    await loadData();
    renderProducts();
    showOverlay("overlay-done");
  };

  window.executeAdd = async function() {
    const name = document.getElementById("new-name").value.trim();
    const price = Number(document.getElementById("new-price").value.trim());
    const category = document.getElementById("new-category").value;
    const nomihoudai = document.getElementById("new-nomihoudai")?.value || "なし";
    const imagePath = typeof addImageDataUrl === "string" ? addImageDataUrl : "";
    if (!name || !price || !category || !imagePath) return;
    const result = await json("/api/admin/products", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, price, category, imagePath })
    });

    // 飲み放題区分で選択されたコースに、追加した商品を対象商品として登録する
    const courseNames = nomihoudai === "なし" ? [] : nomihoudai.split("、");
    for (const courseName of courseNames) {
      const course = courses.find(c => c.name === courseName);
      if (course) {
        await json(`/api/admin/courses/${course.id}/products/${result.id}`, { method: "POST" });
      }
    }

    const doneImg = document.getElementById("add-done-img");
    if (doneImg) {
      if (imagePath) {
        doneImg.src = imagePath;
        doneImg.style.display = "block";
      } else {
        doneImg.removeAttribute("src");
        doneImg.style.display = "none";
      }
    }
    document.getElementById("add-done-name").textContent = "商品名：" + name;
    document.getElementById("add-done-price").textContent = "商品価格：" + price + "円";
    document.getElementById("add-done-category").textContent = "カテゴリー：" + categories.find(c => c.code === category)?.name;
    document.getElementById("add-done-nomihoudai").textContent = courseNames.length ? "飲み放題：" + nomihoudai : "";
    await loadData();
    showOverlay("overlay-add-done");
  };


  /* ========================================
     コース管理（飲み放題の対象商品管理）
  ======================================== */
  window.goCourseList = function() {
    const grid = document.getElementById("course-list-grid");
    grid.innerHTML = "";
    courses.forEach(course => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = course.name;
      button.onclick = () => openCourseAction(course);
      grid.appendChild(button);
    });
    showScreen("screen-course-list");
  };

  function openCourseAction(course) {
    selectedCourse = course;
    document.getElementById("course-action-name").textContent = "コース名：" + course.name;
    showOverlay("overlay-course-action");
  }

  window.closeCourseAction = function() {
    closeAllOverlays();
  };

  window.goCourseCategory = async function(action) {
    selectedCourseAction = action;
    if (!selectedCourse) return;
    courseProducts = await json(`/api/admin/courses/${selectedCourse.id}/products`);

    const grid = document.getElementById("course-category-grid");
    grid.innerHTML = "";
    const targetCategories = categories.filter(category => {
      if (isPlanCategory(category.code)) return false;
      const inCategory = courseProducts.filter(p => p.category === category.code);
      // 商品削除の場合は、このコースに商品が登録されているカテゴリのみ表示する
      if (selectedCourseAction === "remove") {
        return inCategory.some(p => p.included);
      }
      return true;
    });
    targetCategories.forEach(category => {
      const button = document.createElement("button");
      button.className = "btn";
      button.type = "button";
      button.textContent = category.name;
      button.onclick = () => selectCourseCategory(category.code);
      grid.appendChild(button);
    });

    closeAllOverlays();
    showScreen("screen-course-category");
  };

  window.backToCourseAction = function() {
    showScreen("screen-course-list");
    if (selectedCourse) openCourseAction(selectedCourse);
  };

  function selectCourseCategory(categoryCode) {
    selectedCourseCategory = categoryCode;
    const grid = document.getElementById("course-product-grid");
    grid.innerHTML = "";
    courseProducts
      .filter(p => p.category === categoryCode)
      .filter(p => (selectedCourseAction === "remove" ? p.included : !p.included))
      .forEach(product => {
        const button = document.createElement("button");
        button.className = "btn";
        button.type = "button";
        button.textContent = product.name;
        button.onclick = () => executeCourseProductAction(product);
        grid.appendChild(button);
      });
    showScreen("screen-course-products");
  };

  async function executeCourseProductAction(product) {
    if (!selectedCourse) return;
    if (selectedCourseAction === "remove") {
      await json(`/api/admin/courses/${selectedCourse.id}/products/${product.id}`, { method: "DELETE" });
    } else {
      await json(`/api/admin/courses/${selectedCourse.id}/products/${product.id}`, { method: "POST" });
    }

    document.getElementById("course-done-course").textContent = "コース名：" + selectedCourse.name;
    document.getElementById("course-done-product").textContent = "商品名：" + product.name;
    document.getElementById("course-done-action").textContent =
      selectedCourseAction === "remove" ? "商品削除を完了いたしました。" : "商品追加を完了いたしました。";
    showOverlay("overlay-course-done");
  }

  window.finishCourseAction = function() {
    closeAllOverlays();
    showScreen("screen-list");
  };

  document.addEventListener("DOMContentLoaded", loadData);
})();
