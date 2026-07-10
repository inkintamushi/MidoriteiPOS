(function() {
  let products = [];
  let categories = [];
  let courses = [];
  let activeCourseProductIds = new Set();
  let selectedProduct = null;
  let pageTotal = 0;
  let activeFilter = "all";
  let hideSoldOut = true;

  const DEFAULT_ORDER_QTY_MAX = 30;
  const ORDER_QTY_PER_GUEST = 3;
  let orderQtyMax = DEFAULT_ORDER_QTY_MAX;

  // 飲み放題は「個数」ではなく「人数」で数える
  function isPlanCategory(category) {
    return category === "nomi";
  }
  function selectedPlanCourse() {
    const select = document.getElementById("plan-select");
    if (!select || !select.value) return null;
    return courses.find(course => Number(course.id) === Number(select.value)) || null;
  }

  function isIncludedInActiveCourse(product) {
    return !isPlanCategory(product.category) && activeCourseProductIds.has(Number(product.id));
  }

  function effectiveProductPrice(product) {
    return isIncludedInActiveCourse(product) ? 0 : Number(product.price);
  }
  function formatPlanOption(course) {
    return `${course.name}（${Number(course.price).toLocaleString()}円 / ${course.duration}）`;
  }

  function tableNumber() {
    const fromUrl = new URLSearchParams(location.search).get("table");
    if (fromUrl) {
      localStorage.setItem("currentOrderTable", fromUrl);
      return Number(fromUrl);
    }
    return Number(localStorage.getItem("currentOrderTable") || 0);
  }

  // 人数分の飲み放題プランが入っている卓は、その人数×3を注文数の上限にする
  function getOrderQtyMax() {
    const guestCounts = JSON.parse(localStorage.getItem("tableGuestCounts") || "{}");
    const guestCount = parseInt(guestCounts[String(tableNumber())], 10);
    if (Number.isInteger(guestCount) && guestCount > 0) {
      return guestCount * ORDER_QTY_PER_GUEST;
    }
    return DEFAULT_ORDER_QTY_MAX;
  }

  function normalizeQty(value) {
    const qty = parseInt(value, 10);
    if (!Number.isInteger(qty) || qty < 1) {
      return 1;
    }
    return Math.min(qty, orderQtyMax);
  }

  // The qr token proves this device scanned the table's current QR code;
  // without sending it, the server rejects order/checkout/call-staff requests.
  function qrToken() {
    const fromUrl = new URLSearchParams(location.search).get("qr");
    if (fromUrl) {
      localStorage.setItem("currentOrderQr", fromUrl);
      return fromUrl;
    }
    return localStorage.getItem("currentOrderQr") || "";
  }

  async function loadJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
      let message = "";
      try {
        message = (await response.json()).message || "";
      } catch (e) {}
      throw new Error(message || `${url} (${response.status})`);
    }
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
        activeFilter = tab.dataset.filter;
        updateCardVisibility();
      });
    });
  }

  function updateCardVisibility() {
    document.querySelectorAll(".card").forEach(card => {
      const matchesCategory = activeFilter === "all" || card.dataset.category === activeFilter;
      const hiddenBySoldOut = hideSoldOut && card.dataset.soldOut === "true";
      card.style.display = matchesCategory && !hiddenBySoldOut ? "grid" : "none";
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
      const displayPrice = effectiveProductPrice(product);
      card.dataset.price = displayPrice;
      card.dataset.productId = product.id;
      card.dataset.soldOut = product.sold_out ? "true" : "false";
      card.innerHTML = `
        <div class="image">
          <img src="${product.image_path}" alt="${product.name}" style="width:100%;height:100%;object-fit:cover;border-radius:15px;">
        </div>
        <div class="name">${product.name}</div>
        <div class="price">${displayPrice.toLocaleString()}円</div>
        <button class="order-btn" type="button" ${product.sold_out ? "disabled" : ""}>${product.sold_out ? "品切れ" : "注文"}</button>
      `;
      card.querySelector(".order-btn").onclick = () => openOrderModal(product);
      list.appendChild(card);
    });
    updateCardVisibility();
  }

  function openOrderModal(product) {
    selectedProduct = product;
    const modal = document.getElementById("modal");
    const qty = document.getElementById("qty");
    document.getElementById("modal-name").innerText = product.name;
    document.getElementById("modal-img").src = product.image_path;
    document.getElementById("modal-img").alt = product.name;
    const isPlan = isPlanCategory(product.category);
    const planRow = document.getElementById("plan-select-row");
    const planSelect = document.getElementById("plan-select");
    if (planRow && planSelect) {
      planRow.style.display = isPlan ? "flex" : "none";
      planSelect.innerHTML = "";
      if (isPlan) {
        courses.forEach(course => {
          const option = document.createElement("option");
          option.value = course.id;
          option.textContent = formatPlanOption(course);
          planSelect.appendChild(option);
        });
        const defaultCourse = courses.find(course => Number(course.price) === Number(product.price)) || courses[0];
        if (defaultCourse) planSelect.value = String(defaultCourse.id);
      }
    }
    const qtyLabel = document.getElementById("qty-label");
    if (qtyLabel) qtyLabel.textContent = isPlan ? "人数:" : "個数:";
    orderQtyMax = getOrderQtyMax();
    qty.max = orderQtyMax;
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
    const qty = normalizeQty(qtyInput.value);
    const plan = isPlanCategory(selectedProduct.category) ? selectedPlanCourse() : null;
    const orderItem = { productId: selectedProduct.id, quantity: qty };
    if (plan) orderItem.courseId = Number(plan.id);
    let result;
    try {
      result = await loadJson("/api/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tableNumber: table,
          qrToken: qrToken(),
          items: [orderItem]
        })
      });
    } catch (e) {
      document.getElementById("modal").classList.remove("active");
      alert(e.message || "注文に失敗しました。");
      return;
    }

    pageTotal += Number(result.totalPrice || 0);
    document.getElementById("total-area").innerText = `合計：${pageTotal.toLocaleString()}円`;
    if (plan) {
      await initActiveCourseProducts(table);
      renderProducts();
    }
    document.getElementById("modal").classList.remove("active");
    document.getElementById("complete-name").innerText = plan ? `${selectedProduct.name}（${plan.name}）` : selectedProduct.name;
    document.getElementById("complete-qty").innerText = `${isPlanCategory(selectedProduct.category) ? "人数" : "個数"}：${qty}`;
    document.getElementById("complete-img").src = selectedProduct.image_path;
    document.getElementById("complete-img").alt = selectedProduct.name;
    document.getElementById("complete-modal").classList.add("active");
  }


  async function refreshPageTotal() {
    const table = tableNumber();
    if (!table) return;
    try {
      const rows = await loadJson(`/api/orders/history?tableNumber=${table}`);
      pageTotal = rows.reduce((sum, item) => {
        const qty = Number(item.qty || 0) - Number(item.canceled_quantity || 0);
        if (qty <= 0) return sum;
        return sum + qty * Number(item.unit_price || 0);
      }, 0);
      const total = document.getElementById("total-area");
      if (total) total.innerText = `合計：${pageTotal.toLocaleString()}円`;
    } catch (e) {}
  }
  async function checkoutCall() {
    const table = tableNumber();
    let result;
    try {
      result = await loadJson("/api/orders/checkout-call", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tableNumber: table, qrToken: qrToken() })
      });
    } catch (e) {
      alert(e.message || "会計処理に失敗しました。");
      return false;
    }
    document.getElementById("payment-total").innerText = `会計：${Number(result.totalPrice || pageTotal).toLocaleString()}円`;
    return true;
  }

  async function callStaff() {
    const table = tableNumber();
    try {
      await loadJson("/api/orders/call-staff", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tableNumber: table, qrToken: qrToken() })
      });
    } catch (e) {
      alert(e.message || "スタッフ呼出に失敗しました。");
      return false;
    }
    return true;
  }

  function formatRemaining(ms) {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }

  async function fetchRemainingSeconds(table) {
    const session = await loadJson(`/api/orders/session?tableNumber=${table}&qrToken=${encodeURIComponent(qrToken())}`);
    return session.hasCourse ? session.remainingSeconds : null;
  }

  async function initActiveCourseProducts(table) {
    if (!table) return;
    try {
      const session = await loadJson(`/api/orders/session?tableNumber=${table}&qrToken=${encodeURIComponent(qrToken())}`);
      activeCourseProductIds = new Set((session.includedProductIds || []).map(Number));
    } catch (e) {
      activeCourseProductIds = new Set();
    }
  }
  async function initRemainingTime() {
    const label = document.getElementById("remaining-time");
    if (!label) return;
    const table = tableNumber();
    if (!table) return;

    let remainingSeconds;
    try {
      remainingSeconds = await fetchRemainingSeconds(table);
    } catch (e) {
      return;
    }
    if (remainingSeconds == null) return;
    label.style.display = "";

    // endTime is re-derived from Date.now() each time we (re)sync with the
    // server, so only the client clock's rate matters, never its absolute
    // value/timezone. This keeps the countdown correct even if the device's
    // clock is wrong, and the periodic resync below corrects for any drift
    // that accumulates over a long course.
    let endTime = Date.now() + remainingSeconds * 1000;
    let tickId;
    let resyncId;

    const stop = () => {
      clearInterval(tickId);
      clearInterval(resyncId);
    };

    const tick = () => {
      const remainingMs = endTime - Date.now();
      label.textContent = `残り時間：${formatRemaining(remainingMs)}`;
      if (remainingMs <= 0) {
        stop();
      }
    };
    tick();
    tickId = setInterval(tick, 1000);

    resyncId = setInterval(async () => {
      try {
        const fresh = await fetchRemainingSeconds(table);
        if (fresh == null) {
          stop();
          return;
        }
        endTime = Date.now() + fresh * 1000;
      } catch (e) {
        // keep counting down from the last known endTime if the resync fails
      }
    }, 30000);
  }

  document.addEventListener("DOMContentLoaded", async () => {
    categories = await loadJson("/api/categories");
    courses = await loadJson("/api/courses");
    products = await loadJson("/api/products");
    const table = tableNumber();
    await initActiveCourseProducts(table);
    renderCategories();
    renderProducts();
    const tableLabel = document.getElementById("table-number");
    if (tableLabel) tableLabel.textContent = table ? `卓番号：${table}` : "卓番号：未選択";
    await refreshPageTotal();
    initRemainingTime();

    const hideSoldOutCheckbox = document.getElementById("hide-sold-out");
    if (hideSoldOutCheckbox) {
      hideSoldOut = hideSoldOutCheckbox.checked;
      hideSoldOutCheckbox.addEventListener("change", () => {
        hideSoldOut = hideSoldOutCheckbox.checked;
        updateCardVisibility();
      });
    }

    const qtyInput = document.getElementById("qty");
    if (qtyInput) {
      qtyInput.addEventListener("input", () => {
        const qty = parseInt(qtyInput.value, 10);
        if (Number.isInteger(qty) && qty > orderQtyMax) {
          qtyInput.value = orderQtyMax;
        }
      });
      qtyInput.addEventListener("blur", () => {
        qtyInput.value = normalizeQty(qtyInput.value);
      });
    }
    const qtyMinus = document.getElementById("qty-minus");
    const qtyPlus = document.getElementById("qty-plus");
    if (qtyMinus) qtyMinus.onclick = () => { qtyInput.value = normalizeQty(normalizeQty(qtyInput.value) - 1); };
    if (qtyPlus) qtyPlus.onclick = () => { qtyInput.value = normalizeQty(normalizeQty(qtyInput.value) + 1); };

    document.getElementById("no").onclick = () => document.getElementById("modal").classList.remove("active");
    document.getElementById("complete-ok").onclick = () => document.getElementById("complete-modal").classList.remove("active");

    document.getElementById("yes").onclick = submitOrder;
    document.getElementById("account").onclick = () => {
      document.getElementById("payment-total").innerText = `会計：${pageTotal.toLocaleString()}円`;
      document.getElementById("payment-modal").classList.add("active");
    };
    document.getElementById("payment-no").onclick = () => document.getElementById("payment-modal").classList.remove("active");
    const paymentOk = document.getElementById("payment-ok");
    if (paymentOk) paymentOk.onclick = () => document.getElementById("payment-complete-modal").classList.remove("active");
    document.getElementById("payment-yes").onclick = async () => {
      document.getElementById("payment-modal").classList.remove("active");
      if (await checkoutCall()) {
        document.getElementById("payment-complete-modal").classList.add("active");
      }
    };

    document.getElementById("call").onclick = () => document.getElementById("staff-call-modal").classList.add("active");
    document.getElementById("staff-call-no").onclick = () => document.getElementById("staff-call-modal").classList.remove("active");
    document.getElementById("staff-call-ok").onclick = () => document.getElementById("staff-call-complete-modal").classList.remove("active");
    document.getElementById("staff-call-yes").onclick = async () => {
      document.getElementById("staff-call-modal").classList.remove("active");
      if (await callStaff()) {
        document.getElementById("staff-call-complete-modal").classList.add("active");
      }
    };

    document.getElementById("history").onclick = () => document.getElementById("history-modal").classList.add("active");
    document.getElementById("history-no").onclick = () => document.getElementById("history-modal").classList.remove("active");
    document.getElementById("history-yes").onclick = () => {
      location.href = "/order_history";
    };
  });
})();
