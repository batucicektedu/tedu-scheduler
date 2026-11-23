// Müfredatlar sayfasının saf yardımcı işlevleri (ekran bilgisi yok, kolay sınanır).

export const KIND_ORDER = ["UNDERGRADUATE", "MASTER_THESIS", "MASTER_NON_THESIS", "PHD", "MINOR"]

const KIND_LABELS = {
    UNDERGRADUATE: "Lisans",
    MASTER_THESIS: "Tezli yüksek lisans",
    MASTER_NON_THESIS: "Tezsiz yüksek lisans",
    PHD: "Doktora",
    MINOR: "Yandal",
}

export const kindLabel = (kind) => KIND_LABELS[kind] || kind || "—"

export const facultyLabel = (faculty) => {
    if (faculty === "DIGER") return "Diğer"
    return faculty || "—"
}

// Türkçe arama için küçük harfe çevirir; ı ile i aynı sayılır ("ie" -> IE, "bilgisayar" -> BİLGİSAYAR).
export const normalizeSearch = (text) =>
    String(text == null ? "" : text)
        .toLocaleLowerCase("tr")
        .replace(/̇/g, "")
        .replace(/ı/g, "i")
        .trim()

// Süzgeç: fakülte ve tür tam eşleşir, arama ad / kod / etiket içinde geçer.
export const filterPlans = (plans, { faculty = "", kind = "", q = "" } = {}) => {
    const needle = normalizeSearch(q)
    return (plans || []).filter(p => {
        if (faculty && p.faculty !== faculty) return false
        if (kind && p.kind !== kind) return false
        if (!needle) return true
        return [p.programName, p.programCode, p.label].some(v => normalizeSearch(v).indexOf(needle) !== -1)
    })
}

export const distinctFaculties = (plans) =>
    Array.from(new Set((plans || []).map(p => p.faculty).filter(Boolean))).sort((a, b) => a.localeCompare(b, "tr"))

export const distinctKinds = (plans) => {
    const present = new Set((plans || []).map(p => p.kind))
    return KIND_ORDER.filter(k => present.has(k))
}

// 7.5 -> "7,5", 6.0 -> "6", null -> "—"
export const formatNumber = (value) => {
    if (value === null || value === undefined || value === "") return "—"
    const n = Number(value)
    if (!isFinite(n)) return "—"
    return String(parseFloat(n.toFixed(2))).replace(".", ",")
}

export const itemCodeText = (item) => {
    const code = (item && item.code) || ""
    const alt = (item && item.alternativeCode) || ""
    if (code && alt) return `${code} / ${alt}`
    return code || alt || "—"
}

export const itemNameText = (item) => (item && (item.name || item.code)) || "—"

export const isElective = (item) => !!item && item.itemType === "ELECTIVE"

// Yandal gibi ders listesi olmayan plan mı?
export const hasNoItems = (plan) => !plan || ((plan.courseCount || 0) + (plan.electiveCount || 0)) === 0

const pad = (n) => (n < 10 ? "0" + n : String(n))

// "gg.aa.yyyy ss:dd"
export const formatDateTime = (iso) => {
    if (!iso) return ""
    const d = new Date(iso)
    if (isNaN(d.getTime())) return ""
    return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// Ayrıntı başlığı: "Bilgisayar Mühendisliği (CMPE) · Lisans · 2025 · MF"
export const planTitle = (plan) => {
    if (!plan) return ""
    const name = plan.programName || plan.programCode || ""
    const head = plan.programCode && plan.programCode !== name ? `${name} (${plan.programCode})` : name
    return [head, kindLabel(plan.kind), plan.label, facultyLabel(plan.faculty)].filter(Boolean).join(" · ")
}

export const semesterLabel = (n) => `Yarıyıl ${n}`

// İçe aktarma yanıtından sayılar. Yeni / güncellenen sayıları plans[].status alanından sayılır.
export const summarizeImport = (data) => {
    const files = (data && data.files) || []
    let plans = 0, created = 0, updated = 0, warnings = 0
    files.forEach(f => {
        (f.plans || []).forEach(p => {
            plans++
            if (p.status === "NEW") created++
            else if (p.status === "UPDATED") updated++
        })
        warnings += (f.warnings || []).length
    })
    return { files: files.length, plans, created, updated, warnings }
}

export const fileCounts = (file) => {
    const plans = (file && file.plans) || []
    return {
        plans: plans.length,
        created: plans.filter(p => p.status === "NEW").length,
        updated: plans.filter(p => p.status === "UPDATED").length,
    }
}

export const statusLabel = (status, saved) => {
    if (status === "NEW") return "Yeni"
    if (status === "UPDATED") return saved ? "Güncellendi" : "Güncellenecek"
    return status || "—"
}

// .xlsx olmayan ilk dosyanın adı, yoksa null.
export const firstNonXlsx = (files) => {
    const bad = (files || []).find(f => !/\.xlsx$/i.test(f.name || ""))
    return bad ? bad.name : null
}

const NOTHING_SAVED = " Hiçbir şey kaydedilmedi."

// İçe aktarma hatası metni. phase: "check" ya da "save".
export const importErrorText = (err, phase) => {
    const res = err && err.response
    if (!res) {
        return phase === "save"
            ? "Sunucudan yanıt alınamadı; kaydın yapılıp yapılmadığı bilinmiyor. Yeniden kontrol edin."
            : "Sunucuya ulaşılamadı. Dosyayı seçtikten sonra değiştirdiyseniz yeniden seçin."
    }
    if (res.status === 403) return "Yönetici anahtarı hatalı. Hiçbir şey kaydedilmedi."
    if (res.status === 413) return "Dosyalar çok büyük (en çok 20 MB)."
    if (res.status === 400) {
        let msg = ""
        if (typeof res.data === "string") msg = res.data
        else if (res.data && typeof res.data.message === "string") msg = res.data.message
        msg = msg.trim()
        return msg ? msg + NOTHING_SAVED : "Beklenmeyen hata (400)."
    }
    return `Beklenmeyen hata (${res.status}).`
}

export const listErrorText = () => "Müfredat listesi alınamadı."

export const detailErrorText = (err) => {
    if (err && err.response && err.response.status === 404) return "Bu müfredat artık bulunamıyor. Listeyi yenileyin."
    return "Müfredat ayrıntısı alınamadı."
}
