function toggleInputs() {
    const type = document.getElementById('recipientType').value;
    document.getElementById('emailContainer').style.display     = type === 'email'     ? 'block' : 'none';
    document.getElementById('bloodTypeContainer').style.display = type === 'bloodType' ? 'block' : 'none';
}

function showAlert(type, message) {
    document.getElementById('successAlert').style.display = 'none';
    document.getElementById('errorAlert').style.display   = 'none';
    if (type === 'success') {
        document.getElementById('successMessage').textContent = message;
        document.getElementById('successAlert').style.display = 'block';
        setTimeout(() => document.getElementById('successAlert').style.display = 'none', 5000);
    } else {
        document.getElementById('errorMessage').textContent = message;
        document.getElementById('errorAlert').style.display = 'block';
        setTimeout(() => document.getElementById('errorAlert').style.display = 'none', 5000);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function loadAdmins() {
    fetch('AlertsServlet?action=getAdmins')
        .then(res => res.json())
        .then(data => {
            const select = document.getElementById('filterAdmin');
            select.innerHTML = '<option value="">All Admins</option>';
            data.forEach(admin => {
                const option = document.createElement('option');
                option.value = admin.name;
                option.textContent = admin.name + ' (' + admin.email + ')';
                select.appendChild(option);
            });
        })
        .catch(err => console.error('Error loading admins:', err));
}

function loadAlerts() {
    const adminFilter = document.getElementById('filterAdmin').value;
    fetch('AlertsServlet?action=list&admin=' + encodeURIComponent(adminFilter))
        .then(res => res.json())
        .then(data => {
            const tbody = document.getElementById('alertsTableBody');
            if (data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;">
                    <i class="fas fa-inbox" style="font-size:48px;color:#ccc;display:block;margin-bottom:15px;"></i>
                    <p style="color:#999;">No alerts found</p></td></tr>`;
                return;
            }
            tbody.innerHTML = '';
            data.forEach(alert => {
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td><input type="checkbox" class="alert-checkbox" value="${alert.id}" onchange="updateSelection()"></td>
                    <td><strong>${alert.id}</strong></td>
                    <td>${alert.admin}</td>
                    <td>${alert.user_email}</td>
                    <td><strong>${alert.title}</strong></td>
                    <td style="max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${alert.message}">${alert.message}</td>
                    <td>${formatDate(alert.created_at)}</td>
                    <td><button class="btn-action btn-delete-small" onclick="deleteAlert(${alert.id})"><i class="fas fa-trash"></i></button></td>`;
                tbody.appendChild(row);
            });
            updateSelection();
        })
        .catch(err => { console.error('Error loading alerts:', err); showAlert('error', 'Failed to load alerts'); });
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAll');
    document.querySelectorAll('.alert-checkbox').forEach(cb => cb.checked = selectAll.checked);
    updateSelection();
}

function updateSelection() {
    const checkboxes = document.querySelectorAll('.alert-checkbox:checked');
    const count = checkboxes.length;
    document.getElementById('selectionCount').textContent   = `${count} selected`;
    document.getElementById('selectionCount').style.display = count > 0 ? 'inline-block' : 'none';
    document.getElementById('bulkDeleteBtn').style.display  = count > 0 ? 'inline-block' : 'none';
    const total = document.querySelectorAll('.alert-checkbox').length;
    document.getElementById('selectAll').checked = (count === total && total > 0);
}

function bulkDeleteAlerts() {
    const ids = Array.from(document.querySelectorAll('.alert-checkbox:checked')).map(cb => cb.value);
    if (ids.length === 0) { showAlert('error', 'Please select at least one alert'); return; }
    if (!confirm(`Are you sure you want to delete ${ids.length} alert(s)?`)) return;
    Promise.all(ids.map(id => fetch('AlertsServlet?action=delete&id=' + id).then(res => res.text())))
        .then(results => {
            const successCount = results.filter(r => r === 'success').length;
            showAlert(successCount === ids.length ? 'success' : 'error',
                successCount === ids.length ? `Successfully deleted ${successCount} alert(s)` : `Deleted ${successCount} of ${ids.length} alerts. Some failed.`);
            document.getElementById('selectAll').checked = false;
            loadAlerts();
        })
        .catch(err => { console.error('Error:', err); showAlert('error', 'Error deleting alerts'); });
}

function deleteAlert(id) {
    if (!confirm('Are you sure you want to delete this alert?')) return;
    fetch('AlertsServlet?action=delete&id=' + id)
        .then(res => res.text())
        .then(data => {
            if (data === 'success') { showAlert('success', 'Alert deleted successfully'); loadAlerts(); }
            else showAlert('error', 'Failed to delete alert');
        })
        .catch(err => { console.error('Error:', err); showAlert('error', 'Error deleting alert'); });
}

function sendAlert() {
    const title   = document.getElementById('alertTitle').value.trim();
    const message = document.getElementById('alertMessage').value.trim();
    const type    = document.getElementById('recipientType').value;
    const email   = document.getElementById('userEmail').value.trim();
    const blood   = document.getElementById('bloodTypeSelect').value;
    if (!title)                         { showAlert('error', 'Please enter alert title');   return; }
    if (!message)                       { showAlert('error', 'Please enter alert message'); return; }
    if (type === 'email' && !email)     { showAlert('error', 'Please enter user email');    return; }
    if (type === 'bloodType' && !blood) { showAlert('error', 'Please select blood type');   return; }
    const formData = new URLSearchParams();
    formData.append('title', title); formData.append('message', message);
    formData.append('type', type);   formData.append('email', email); formData.append('blood', blood);
    fetch('AlertsServlet', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: formData })
        .then(res => res.text())
        .then(data => {
            if (data.trim() === 'success') {
                showAlert('success', 'Alert sent successfully!');
                document.getElementById('alertTitle').value = '';
                document.getElementById('alertMessage').value = '';
                document.getElementById('userEmail').value = '';
                document.getElementById('bloodTypeSelect').value = '';
                document.getElementById('recipientType').value = 'all';
                toggleInputs(); loadAlerts();
            } else { showAlert('error', 'Failed to send alert. ' + data); }
        })
        .catch(err => { console.error('Error:', err); showAlert('error', 'Error sending alert'); });
}

window.onload = function () { loadAdmins(); loadAlerts(); };