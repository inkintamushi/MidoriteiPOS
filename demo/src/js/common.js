// モーダル表示
function showModal() {
  const modal = document.querySelector('.modal');
  if (modal) {
    modal.classList.add('active');
  }
}

// モーダル非表示
function hideModal() {
  const modal = document.querySelector('.modal');
  if (modal) {
    modal.classList.remove('active');
  }
}

// OKボタンにイベント付与
document.addEventListener('DOMContentLoaded', () => {
  const okBtn = document.querySelector('.ok-btn');
  if (okBtn) {
    okBtn.addEventListener('click', hideModal);
  }
});