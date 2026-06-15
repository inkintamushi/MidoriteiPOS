(function() {
  function goLogin() {
    location.replace("login.html");
  }

  window.logoutStaff = async function() {
    await fetch("/api/auth/logout", { method: "POST" });
    goLogin();
  };

  document.addEventListener("DOMContentLoaded", function() {
    const header = document.querySelector(".header");
    if (!header || document.getElementById("logout-btn")) return;

    header.classList.add("employee-header");

    const button = document.createElement("button");
    button.id = "logout-btn";
    button.className = "logout-btn";
    button.type = "button";
    button.textContent = "ログアウト";
    button.onclick = window.logoutStaff;
    header.appendChild(button);
  });
})();
