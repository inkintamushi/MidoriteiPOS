(function() {
  function goLogin() {
    location.replace("login.html");
  }

  window.logoutStaff = async function() {
    await fetch("/api/auth/logout", { method: "POST" });
    goLogin();
  };

  document.addEventListener("DOMContentLoaded", function() {
    const phone = document.querySelector(".phone");
    if (!phone || document.getElementById("logout-btn")) return;

    const button = document.createElement("button");
    button.id = "logout-btn";
    button.className = "logout-btn";
    button.type = "button";
    button.textContent = "ログアウト";
    button.onclick = function() {
      document.getElementById("logout-confirm-overlay").classList.add("active");
    };
    phone.appendChild(button);

    const overlay = document.createElement("div");
    overlay.id = "logout-confirm-overlay";
    overlay.className = "logout-overlay";
    overlay.innerHTML = `
      <div class="logout-modal-card">
        <div class="logout-modal-title">ログアウト</div>
        <div class="logout-modal-text">ログアウトしてもよろしいですか。</div>
        <div class="logout-modal-buttons">
          <button type="button" id="logout-no-btn">No</button>
          <button type="button" id="logout-yes-btn">Yes</button>
        </div>
      </div>
    `;
    phone.appendChild(overlay);

    document.getElementById("logout-no-btn").onclick = function() {
      overlay.classList.remove("active");
    };
    document.getElementById("logout-yes-btn").onclick = window.logoutStaff;
  });
})();
