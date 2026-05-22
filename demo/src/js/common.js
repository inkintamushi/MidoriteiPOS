const modal = document.getElementById("modal");
const modalName = document.getElementById("modal-name");
const modalImg = document.getElementById("modal-img");
const modalQty = document.getElementById("modal-qty");

document.querySelectorAll(".order-btn").forEach(btn => {
  btn.onclick = function() {

    const card = this.parentElement;

    const name = card.querySelector(".name").innerText;
    const img = card.querySelector("img").src;

    modalName.innerText = name;
    modalImg.src = img;

    modalQty.value = 1;

    modal.classList.add("active");
  };
});

// No
document.getElementById("no").onclick = () => {
  modal.classList.remove("active");
};

// Yes
document.getElementById("yes").onclick = () => {
  const qty = modalQty.value;
  alert("注文しました！ 個数：" + qty);
  modal.classList.remove("active");
};