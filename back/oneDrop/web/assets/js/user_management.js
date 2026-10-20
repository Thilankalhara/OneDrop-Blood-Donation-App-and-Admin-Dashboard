let allUsers = [], filteredUsers = [], currentPage = 1, usersPerPage = 10;

async function loadUsers() {
    try {
        const res = await fetch('UserManagementServlet', { credentials: 'include' });
        if (!res.ok) throw new Error('Failed to fetch');
        allUsers = await res.json();
        filteredUsers = [...allUsers];
        renderUsers();
    } catch (err) {
        console.error('Error:', err);
        document.getElementById('userTableBody').innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-exclamation-triangle" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
            Failed to load users. Please refresh.</td></tr>`;
    }
}

function renderUsers() {
    const tbody = document.getElementById('userTableBody');
    const start = (currentPage - 1) * usersPerPage;
    const page  = filteredUsers.slice(start, start + usersPerPage);
    if (page.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-inbox" style="font-size:48px;margin-bottom:15px;display:block;opacity:0.3;"></i>No users found</td></tr>`;
        renderPagination(); return;
    }
    tbody.innerHTML = page.map(u => `
        <tr>
            <td><strong>#${u.user_id}</strong></td>
            <td><strong>${u.name}</strong></td>
            <td>${u.email}</td>
            <td>${u.mobile || 'N/A'}</td>
            <td><span class="blood-badge">${u.blood_type}</span></td>
            <td>${u.gender}</td>
            <td><span class="badge-status ${u.status === 'ACTIVE' ? 'badge-active' : 'badge-inactive'}">${u.status}</span></td>
            <td>
                <button class="btn-action btn-view" onclick="viewUser(${u.user_id})"><i class="fas fa-eye"></i></button>
                <button class="btn-action ${u.status === 'ACTIVE' ? 'btn-deactivate' : 'btn-activate'}"
                        onclick="toggleUserStatus(${u.user_id},'${u.status}')"
                        title="${u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}">
                    <i class="fas ${u.status === 'ACTIVE' ? 'fa-ban' : 'fa-check'}"></i>
                </button>
            </td>
        </tr>`).join('');
    renderPagination();
}

function viewUser(id) {
    const u = allUsers.find(x => x.user_id === id); if (!u) return;
    document.getElementById('modalUserId').innerText        = u.user_id;
    document.getElementById('modalUserName').innerText      = u.name;
    document.getElementById('modalUserEmail').innerText     = u.email;
    document.getElementById('modalUserMobile').innerText    = u.mobile || 'N/A';
    document.getElementById('modalUserBloodType').innerHTML = `<span class="blood-badge">${u.blood_type}</span>`;
    document.getElementById('modalUserGender').innerText    = u.gender;
    document.getElementById('modalUserStatus').innerHTML    = `<span class="badge-status ${u.status==='ACTIVE'?'badge-active':'badge-inactive'}">${u.status}</span>`;
    document.getElementById('modalUserCreated').innerText   = new Date(u.created_at).toLocaleDateString('en-US',{year:'numeric',month:'long',day:'numeric'});
    document.getElementById('modalUserImage').src           = u.profile_image || 'https://via.placeholder.com/120';
    document.getElementById('modalUserDonations').innerText = u.donation_count || 0;
    document.getElementById('viewUserModal').style.display  = 'block';
}

function toggleUserStatus(id, status) {
    const u = allUsers.find(x => x.user_id === id); if (!u) return;
    const newStatus  = status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const actionText = newStatus === 'ACTIVE' ? 'activate' : 'deactivate';
    if (!confirm(`Are you sure you want to ${actionText} user "${u.name}"?`)) return;
    fetch('UserManagementServlet', {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `action=toggleStatus&userId=${id}&currentStatus=${status}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) { u.status = newStatus; applyFilters(); showAlert('success', `User "${u.name}" is now ${newStatus}`); }
        else showAlert('error', 'Failed to update user status');
    })
    .catch(() => showAlert('error', 'Error updating user status'));
}

function showAlert(type, message) {
    const el = document.getElementById('alertBox');
    el.className = 'alert-box alert-' + type;
    el.innerHTML = '<i class="fas fa-' + (type==='success'?'check-circle':'exclamation-circle') + '"></i> ' + message;
    el.style.display = 'block';
    setTimeout(() => el.style.display = 'none', 4000);
}

document.getElementById('searchInput').addEventListener('input',      applyFilters);
document.getElementById('statusFilter').addEventListener('change',    applyFilters);
document.getElementById('bloodTypeFilter').addEventListener('change', applyFilters);
document.getElementById('genderFilter').addEventListener('change',    applyFilters);

function applyFilters() {
    const s=document.getElementById('searchInput').value.toLowerCase();
    const status=document.getElementById('statusFilter').value;
    const blood=document.getElementById('bloodTypeFilter').value;
    const gender=document.getElementById('genderFilter').value;
    filteredUsers = allUsers.filter(u =>
        (!status || u.status===status) && (!blood || u.blood_type===blood) &&
        (!gender || u.gender===gender) &&
        (u.name.toLowerCase().includes(s) || u.email.toLowerCase().includes(s))
    );
    currentPage = 1; renderUsers();
}

function renderPagination() {
    const total = Math.ceil(filteredUsers.length / usersPerPage);
    const el = document.getElementById('pagination');
    if (total <= 1) { el.innerHTML = ''; return; }
    let html = '';
    if (currentPage > 1) html += `<button onclick="changePage(${currentPage-1})"><i class="fas fa-chevron-left"></i> Previous</button>`;
    for (let i = 1; i <= total; i++) {
        if (i===1 || i===total || (i>=currentPage-1 && i<=currentPage+1))
            html += `<button class="${i===currentPage?'active':''}" onclick="changePage(${i})">${i}</button>`;
        else if (i===currentPage-2 || i===currentPage+2)
            html += `<button disabled>...</button>`;
    }
    if (currentPage < total) html += `<button onclick="changePage(${currentPage+1})">Next <i class="fas fa-chevron-right"></i></button>`;
    el.innerHTML = html;
}

function changePage(page) { currentPage = page; renderUsers(); window.scrollTo({top:0,behavior:'smooth'}); }

window.onclick = function(e) { if (e.target===document.getElementById('viewUserModal')) document.getElementById('viewUserModal').style.display='none'; };

window.addEventListener('DOMContentLoaded', loadUsers);