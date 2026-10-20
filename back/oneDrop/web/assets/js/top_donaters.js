let allDonaters = [], filteredDonaters = [];

async function loadDonaters() {
    try {
        const res = await fetch('TopDonatersServlet', { credentials: 'include' });
        allDonaters = await res.json();
        filteredDonaters = [...allDonaters];
        renderLeaderboard();
    } catch (err) {
        console.error('Error:', err);
        document.getElementById("leaderboardTableBody").innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-exclamation-triangle" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
            Failed to load top donaters.</td></tr>`;
    }
}

function renderLeaderboard() {
    const tbody = document.getElementById("leaderboardTableBody");
    filteredDonaters.sort((a, b) => b.total_points - a.total_points);
    if (filteredDonaters.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-trophy" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
            No donaters found.</td></tr>`;
        return;
    }
    tbody.innerHTML = filteredDonaters.map((d, i) => {
        const rank = i + 1;
        const cls  = rank === 1 ? 'rank-1' : rank === 2 ? 'rank-2' : rank === 3 ? 'rank-3' : '';
        const icon = rank === 1 ? '<i class="fas fa-trophy trophy-icon gold"></i>'
                   : rank === 2 ? '<i class="fas fa-trophy trophy-icon silver"></i>'
                   : rank === 3 ? '<i class="fas fa-trophy trophy-icon bronze"></i>' : '';
        const last = d.last_donation ? new Date(d.last_donation).toLocaleDateString('en-US',{year:'numeric',month:'short',day:'numeric'}) : '—';
        return `<tr class="${cls}">
            <td>${icon}<strong>${rank}</strong></td>
            <td><strong>${d.name}</strong></td>
            <td>${d.email}</td>
            <td><span class="blood-badge">${d.blood_type}</span></td>
            <td><strong>${d.total_donations}</strong></td>
            <td><strong>${d.total_points}</strong></td>
            <td>${last}</td>
            <td><button class="btn-action btn-view" onclick="viewDonater(${d.user_id})"><i class="fas fa-eye"></i></button></td>
        </tr>`;
    }).join("");
}

function viewDonater(id) {
    const d = allDonaters.find(x => x.user_id === id); if (!d) return;
    document.getElementById("modalName").textContent         = d.name;
    document.getElementById("modalEmail").textContent        = d.email;
    document.getElementById("modalBloodType").textContent    = d.blood_type;
    document.getElementById("modalDonations").textContent    = d.total_donations;
    document.getElementById("modalPoints").textContent       = d.total_points;
    document.getElementById("modalLastDonation").textContent = d.last_donation
        ? new Date(d.last_donation).toLocaleDateString('en-US',{year:'numeric',month:'short',day:'numeric'}) : '—';
    const rank = [...filteredDonaters].sort((a,b)=>b.total_points-a.total_points).findIndex(x=>x.user_id===id)+1;
    document.getElementById("modalRank").innerHTML = rank<=3
        ? `<i class="fas fa-trophy ${rank===1?'gold':rank===2?'silver':'bronze'}"></i> #${rank}` : '#'+rank;
    document.getElementById("donaterModal").style.display = "block";
}

function closeModal() { document.getElementById("donaterModal").style.display = "none"; }
window.onclick = function(e) { if (e.target===document.getElementById('donaterModal')) closeModal(); };

const filterType=document.getElementById("filterType"), filterStart=document.getElementById("filterDateStart"), filterEnd=document.getElementById("filterDateEnd");
document.getElementById("searchInput").addEventListener("input", applyFilters);
filterType.addEventListener("change", () => {
    filterStart.style.display = filterType.value!=='all' ? 'inline-block' : 'none';
    filterEnd.style.display   = filterType.value==='custom' ? 'inline-block' : 'none';
    applyFilters();
});
filterStart.addEventListener("change", applyFilters);
filterEnd.addEventListener("change",   applyFilters);

function applyFilters() {
    const q=document.getElementById("searchInput").value.toLowerCase(), type=filterType.value, s=filterStart.value, e=filterEnd.value;
    let temp=allDonaters.filter(d=>d.name.toLowerCase().includes(q)||d.email.toLowerCase().includes(q));
    if (type==="day"    && s) temp=temp.filter(d=>d.last_donation===s);
    if (type==="month"  && s) { const [y,m]=s.split("-"); temp=temp.filter(d=>{if(!d.last_donation)return false;const dt=new Date(d.last_donation);return dt.getFullYear()==y&&(dt.getMonth()+1)==m;}); }
    if (type==="year"   && s) { const y=s.split("-")[0]; temp=temp.filter(d=>{if(!d.last_donation)return false;return new Date(d.last_donation).getFullYear()==y;}); }
    if (type==="custom" && s && e) { const sd=new Date(s),ed=new Date(e); temp=temp.filter(d=>{if(!d.last_donation)return false;const dt=new Date(d.last_donation);return dt>=sd&&dt<=ed;}); }
    filteredDonaters=temp; renderLeaderboard();
}

window.addEventListener('DOMContentLoaded', loadDonaters);