let currentFilters = { period: 'all', status: '', search: '', startDate: '', endDate: '' };

function handlePeriodChange() {
    const period = document.getElementById('filterPeriod').value;
    if (period === 'custom') { document.getElementById('dateRangeInputs').style.display = 'flex'; }
    else { document.getElementById('dateRangeInputs').style.display = 'none'; currentFilters.period = period; currentFilters.startDate = ''; currentFilters.endDate = ''; loadDonations(); }
}

function applyDateRange() {
    const s = document.getElementById('startDate').value, e = document.getElementById('endDate').value;
    if (!s || !e) { alert('Please select both start and end dates'); return; }
    if (new Date(s) > new Date(e)) { alert('Start date must be before end date'); return; }
    currentFilters.period = 'custom'; currentFilters.startDate = s; currentFilters.endDate = e;
    loadDonations();
}

function loadDonations() {
    currentFilters.status = document.getElementById('filterStatus').value;
    currentFilters.search = document.getElementById('searchUser').value.trim();
    const params = new URLSearchParams({ action: 'list', period: currentFilters.period, status: currentFilters.status, search: currentFilters.search });
    if (currentFilters.period === 'custom') { params.append('startDate', currentFilters.startDate); params.append('endDate', currentFilters.endDate); }
    fetch('MonetaryDonationsServlet?' + params.toString(), { credentials: 'include' })
        .then(r => r.json())
        .then(data => { renderDonations(data.donations); updateStatistics(data.stats); })
        .catch(err => { console.error('Error:', err); showError(); });
}

function renderDonations(donations) {
    const tbody = document.getElementById('donationsTableBody');
    if (!donations || donations.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;">
            <i class="fas fa-inbox" style="font-size:48px;color:#ccc;display:block;margin-bottom:15px;"></i>
            <p style="color:#999;">No donations found</p></td></tr>`;
        return;
    }
    tbody.innerHTML = '';
    donations.forEach(d => {
        let badge = d.status === 'PENDING' ? 'badge-pending' : d.status === 'FAILED' ? 'badge-failed' : 'badge-success';
        const row = document.createElement('tr');
        row.innerHTML = `
            <td><strong>#${d.id}</strong></td>
            <td><strong>${d.donor_name}</strong></td>
            <td>${d.donor_email}</td>
            <td><span class="amount-badge">$${parseFloat(d.amount).toFixed(2)}</span></td>
            <td><span class="badge-status ${badge}">${d.status}</span></td>
            <td>${formatDate(d.donated_at)}</td>`;
        tbody.appendChild(row);
    });
}

function updateStatistics(stats) {
    if (!stats) return;
    document.getElementById('totalAmount').textContent = '$' + parseFloat(stats.totalAmount  || 0).toFixed(2);
    document.getElementById('totalCount').textContent  = stats.totalCount  || 0;
    document.getElementById('totalDonors').textContent = stats.totalDonors || 0;
    document.getElementById('avgDonation').textContent = '$' + parseFloat(stats.avgDonation || 0).toFixed(2);
}

function showError() {
    document.getElementById('donationsTableBody').innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;color:#C62828;">
        <i class="fas fa-exclamation-triangle" style="font-size:48px;display:block;margin-bottom:15px;"></i>
        <p><strong>Error loading donations</strong></p></td></tr>`;
}

function buildExportParams(format) {
    const p = new URLSearchParams({ action: 'export', format, period: currentFilters.period, status: currentFilters.status, search: currentFilters.search });
    if (currentFilters.period === 'custom') { p.append('startDate', currentFilters.startDate); p.append('endDate', currentFilters.endDate); }
    return p.toString();
}

function exportToExcel() { window.location.href = 'MonetaryDonationsServlet?' + buildExportParams('excel'); }
function exportToPDF()   { window.location.href = 'MonetaryDonationsServlet?' + buildExportParams('pdf'); }

window.onload = function () { loadDonations(); };