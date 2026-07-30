const form = document.querySelector("#query-form");
const tokenInput = document.querySelector("#token");
const localTokenButton = document.querySelector("#local-token");
const statusDot = document.querySelector("#status-dot");
const statusText = document.querySelector("#status-text");
const creditTotal = document.querySelector("#credit-total");
const debitTotal = document.querySelector("#debit-total");
const pageTotal = document.querySelector("#page-total");
const requestPath = document.querySelector("#request-path");
const tableBody = document.querySelector("#transactions-body");

const formatMoney = (money) => {
    if (!money) {
        return "--";
    }
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: money.currency,
        currencyDisplay: "code"
    }).format(Number(money.amount));
};

const setStatus = (message, mode = "idle") => {
    statusText.textContent = message;
    statusDot.className = `status-dot ${mode === "ok" ? "ok" : mode === "error" ? "error" : ""}`;
};

const setEmptyRows = (message) => {
    tableBody.innerHTML = "";
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 4;
    cell.className = "empty";
    cell.textContent = message;
    row.append(cell);
    tableBody.append(row);
};

const renderTransactions = (transactions) => {
    tableBody.innerHTML = "";
    if (!transactions.length) {
        setEmptyRows("No transactions found for this month and page.");
        return;
    }

    transactions.forEach((transaction) => {
        const row = document.createElement("tr");
        const amount = Number(transaction.amount.amount);

        const date = document.createElement("td");
        date.textContent = transaction.valueDate;

        const description = document.createElement("td");
        description.textContent = transaction.description;

        const iban = document.createElement("td");
        iban.textContent = transaction.accountIban;

        const amountCell = document.createElement("td");
        amountCell.className = `numeric ${amount >= 0 ? "amount-credit" : "amount-debit"}`;
        amountCell.textContent = formatMoney(transaction.amount);

        row.append(date, description, iban, amountCell);
        tableBody.append(row);
    });
};

const buildUrl = (formData) => {
    const params = new URLSearchParams({
        month: formData.get("month"),
        targetCurrency: String(formData.get("targetCurrency")).toUpperCase(),
        page: formData.get("page"),
        size: formData.get("size")
    });
    return `/api/v1/transactions?${params.toString()}`;
};

localTokenButton.addEventListener("click", () => {
    tokenInput.value = "local-test-token";
    tokenInput.focus();
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitButton = form.querySelector("button[type='submit']");
    const formData = new FormData(form);
    const token = String(formData.get("token")).trim();
    const url = buildUrl(formData);

    if (!token) {
        setStatus("Token required", "error");
        tokenInput.focus();
        return;
    }

    requestPath.textContent = url;
    setStatus("Fetching transactions");
    submitButton.disabled = true;

    try {
        const response = await fetch(url, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        const body = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(body.message || `Request failed with HTTP ${response.status}`);
        }

        creditTotal.textContent = formatMoney(body.totalCredit);
        debitTotal.textContent = formatMoney(body.totalDebit);
        pageTotal.textContent = `${body.page.totalElements} rows`;
        renderTransactions(body.transactions);
        setStatus(`Loaded page ${body.page.page + 1} of ${Math.max(body.page.totalPages, 1)}`, "ok");
    } catch (error) {
        creditTotal.textContent = "--";
        debitTotal.textContent = "--";
        pageTotal.textContent = "--";
        setEmptyRows(error.message);
        setStatus("Request failed", "error");
    } finally {
        submitButton.disabled = false;
    }
});
