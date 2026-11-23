import {
    filterPlans, formatNumber, itemCodeText, itemNameText, normalizeSearch, summarizeImport,
    importErrorText, formatDateTime, hasNoItems, planTitle, firstNonXlsx, statusLabel,
} from "./curriculaUtils"

const plans = [
    { id: 1, faculty: "MF", programCode: "IE", programName: "Endüstri Mühendisliği", kind: "UNDERGRADUATE", label: "2021" },
    { id: 2, faculty: "MF", programCode: "CMPE", programName: "Bilgisayar Mühendisliği", kind: "UNDERGRADUATE", label: "2025" },
    { id: 3, faculty: "YANDAL", programCode: "Y", programName: "Ekonomi Yandal", kind: "MINOR", label: "2022" },
]

test("arama Türkçe harflerle büyük küçük fark etmez", () => {
    expect(filterPlans(plans, { q: "ie" }).map(p => p.id)).toEqual([1])
    expect(filterPlans(plans, { q: "IE" }).map(p => p.id)).toEqual([1])
    expect(filterPlans(plans, { q: "bilgisayar" }).map(p => p.id)).toEqual([2])
    expect(filterPlans(plans, { q: "BİLGİSAYAR" }).map(p => p.id)).toEqual([2])
    expect(normalizeSearch("ISIK")).toBe("isik")
})

test("üç süzgeç birlikte çalışır", () => {
    expect(filterPlans(plans, { faculty: "MF", kind: "UNDERGRADUATE", q: "2025" }).map(p => p.id)).toEqual([2])
    expect(filterPlans(plans, { faculty: "MF", kind: "MINOR" })).toEqual([])
    expect(filterPlans(plans)).toHaveLength(3)
})

test("sayılar virgüllü yazılır, kesilmez", () => {
    expect(formatNumber(7.5)).toBe("7,5")
    expect(formatNumber("6.00")).toBe("6")
    expect(formatNumber(0)).toBe("0")
    expect(formatNumber(null)).toBe("—")
})

test("ders kodu ve adı", () => {
    expect(itemCodeText({ code: "TUR 101", alternativeCode: "TUR 150" })).toBe("TUR 101 / TUR 150")
    expect(itemCodeText({ code: "", alternativeCode: null })).toBe("—")
    expect(itemNameText({ code: "CC", name: "" })).toBe("CC")
})

test("içe aktarma özeti durumdan sayılır", () => {
    const s = summarizeImport({ files: [
        { plans: [{ status: "NEW" }, { status: "UPDATED" }], warnings: [{}] },
        { plans: [{ status: "NEW" }], warnings: [] },
    ] })
    expect(s).toEqual({ files: 2, plans: 3, created: 2, updated: 1, warnings: 1 })
    expect(statusLabel("UPDATED", false)).toBe("Güncellenecek")
    expect(statusLabel("UPDATED", true)).toBe("Güncellendi")
})

test("hata metinleri", () => {
    expect(importErrorText({ response: { status: 403 } }, "check")).toMatch(/anahtarı hatalı/)
    expect(importErrorText({ response: { status: 400, data: "Bozuk dosya: a.xlsx" } }, "check")).toBe("Bozuk dosya: a.xlsx Hiçbir şey kaydedilmedi.")
    expect(importErrorText({ response: { status: 400, data: { message: "x." } } }, "save")).toBe("x. Hiçbir şey kaydedilmedi.")
    expect(importErrorText({ response: { status: 500 } }, "save")).toBe("Beklenmeyen hata (500).")
    expect(importErrorText({}, "save")).toMatch(/bilinmiyor/)
    expect(importErrorText({}, "check")).toMatch(/ulaşılamadı/)
})

test("yardımcılar", () => {
    expect(hasNoItems({ courseCount: 0, electiveCount: 0 })).toBe(true)
    expect(hasNoItems({ courseCount: 3, electiveCount: 0 })).toBe(false)
    expect(planTitle(plans[1])).toBe("Bilgisayar Mühendisliği (CMPE) · Lisans · 2025 · MF")
    expect(firstNonXlsx([{ name: "a.xlsx" }, { name: "b.xls" }])).toBe("b.xls")
    expect(formatDateTime("2026-11-23T09:05:00")).toBe("23.11.2026 09:05")
    expect(formatDateTime("")).toBe("")
})
