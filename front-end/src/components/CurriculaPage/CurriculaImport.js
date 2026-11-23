import React, { useCallback, useEffect, useRef, useState } from "react";
import {
    Accordion, AccordionDetails, AccordionSummary, Button, Chip, CircularProgress, Table,
    TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import ExpandMoreIcon from "@material-ui/icons/ExpandMore";
import CancelIcon from "@material-ui/icons/Cancel";
import axios from "axios";
import { CoursePlanService } from "../../service";
import {
    fileCounts, firstNonXlsx, formatNumber, importErrorText, kindLabel, statusLabel, summarizeImport,
} from "./curriculaUtils";

const CHECK_BASE = "Kontrol tamamlandı, henüz kaydedilmedi."

// Kontrol ya da kayıt sonucunun özeti: Alert, Chip'ler, dosya dosya ayrıntı, müfredat tablosu.
const ImportSummary = ({ data, saved }) => {
    const [showTable, setShowTable] = useState(false)
    const sum = summarizeImport(data)
    const files = data.files || []
    const warnText = sum.warnings > 0
        ? ` ${sum.warnings} uyarı var; kaydetmeye engel değil, aşağıda okuyun.` : ""
    return (
        <div className="import-summary">
            {saved ? (
                <Alert severity="success">
                    {`Müfredatlar kaydedildi: ${data.plansCreated != null ? data.plansCreated : sum.created} yeni, ${data.plansUpdated != null ? data.plansUpdated : sum.updated} güncellendi, ${data.itemsSaved != null ? data.itemsSaved : 0} satır.`}
                </Alert>
            ) : (
                <Alert severity={sum.warnings > 0 ? "warning" : "info"}>{CHECK_BASE + warnText}</Alert>
            )}
            <div className="summary-chips">
                <Chip label={`Dosya: ${sum.files}`}/>
                <Chip label={`Müfredat: ${sum.plans}`}/>
                <Chip label={`Yeni: ${sum.created}`}/>
                <Chip label={`${saved ? "Güncellenen" : "Güncellenecek"}: ${sum.updated}`}/>
                <Chip label={`Uyarı: ${sum.warnings}`}/>
            </div>
            {sum.plans === 0 && <Alert severity="warning">Dosyalarda müfredat bulunamadı.</Alert>}

            {files.map((f, i) => {
                const c = fileCounts(f)
                const warnings = f.warnings || []
                const skipped = f.skippedSheets || []
                return (
                    <div className="import-file" key={`${f.fileName}-${i}`}>
                        <Typography variant="subtitle2" className="wrap-text">
                            {f.fileName}{f.faculty === "YANDAL" ? " · Yandal dosyası" : ""}
                        </Typography>
                        <Typography variant="body2">
                            {`${c.plans} müfredat: ${c.created} yeni, ${c.updated} ${saved ? "güncellendi" : "güncellenecek"}`}
                        </Typography>
                        {skipped.length > 0 && (
                            <Typography variant="body2" className="muted wrap-text">
                                Atlanan sayfalar: {skipped.join(", ")}
                            </Typography>
                        )}
                        {warnings.length > 0 ? (
                            <div>
                                <Typography variant="body2" className="warnings-title">Uyarılar</Typography>
                                <ul className="warnings-list">
                                    {warnings.map((w, j) => (
                                        <li key={j} className="wrap-text">{w.sheet ? `${w.sheet}: ` : ""}{w.message}</li>
                                    ))}
                                </ul>
                            </div>
                        ) : (
                            <Typography variant="body2" className="muted">Uyarı yok</Typography>
                        )}
                    </div>
                )
            })}

            {sum.plans > 0 && (
                <>
                    <Button size="small" variant="outlined" onClick={() => setShowTable(s => !s)}
                            aria-expanded={showTable}>
                        {showTable ? "Müfredatları gizle" : "Müfredatları göster"}
                    </Button>
                    {showTable && (
                        <TableContainer className="summary-table-box">
                            <Table size="small" className="summary-table">
                                <TableHead>
                                    <TableRow>
                                        <TableCell>Program</TableCell>
                                        <TableCell>Tür</TableCell>
                                        <TableCell>Müfredat</TableCell>
                                        <TableCell align="right">Yarıyıl</TableCell>
                                        <TableCell align="right">Ders</TableCell>
                                        <TableCell align="right">Seçmeli</TableCell>
                                        <TableCell align="right">Kredi</TableCell>
                                        <TableCell align="right">AKTS</TableCell>
                                        <TableCell>Durum</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {files.map((f, i) => (f.plans || []).map((p, j) => (
                                        <TableRow key={`${i}-${j}`}>
                                            <TableCell>{p.programName || p.programCode}</TableCell>
                                            <TableCell>{kindLabel(p.kind)}</TableCell>
                                            <TableCell>{p.label}</TableCell>
                                            <TableCell align="right">{p.semesters}</TableCell>
                                            <TableCell align="right">{p.courses}</TableCell>
                                            <TableCell align="right">{p.electives}</TableCell>
                                            <TableCell align="right">{formatNumber(p.totalCredits)}</TableCell>
                                            <TableCell align="right">{formatNumber(p.totalEcts)}</TableCell>
                                            <TableCell>{statusLabel(p.status, saved)}</TableCell>
                                        </TableRow>
                                    )))}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    )}
                </>
            )}
        </div>
    )
}

// autoOpen: liste yüklenince boşsa true, doluysa false olur; yalnız ilk kez uygulanır.
const CurriculaImport = ({ autoOpen, onSaved }) => {
    const [expanded, setExpanded] = useState(false)
    const decided = useRef(false)
    const [key, setKey] = useState("")
    const [files, setFiles] = useState([])
    const [busy, setBusy] = useState("")          // "", "check", "save"
    const [check, setCheck] = useState(null)      // başarılı kontrol yanıtı
    const [result, setResult] = useState(null)    // { data, saved } ekranda gösterilen özet
    const [error, setError] = useState("")
    const keyRef = useRef(null)
    const focusKey = useRef(false)
    const cancelRef = useRef(null)
    const runId = useRef(0)
    const mounted = useRef(true)

    useEffect(() => () => {
        mounted.current = false
        if (cancelRef.current) cancelRef.current.cancel()
    }, [])

    useEffect(() => {
        if (autoOpen !== undefined && !decided.current) {
            decided.current = true
            setExpanded(!!autoOpen)
        }
    }, [autoOpen])

    // İstek sürerken alan kilitli olduğundan odak, kilit açılınca verilir.
    useEffect(() => {
        if (!busy && focusKey.current) {
            focusKey.current = false
            if (keyRef.current) keyRef.current.focus()
        }
    }, [busy])

    const resetResults = () => { setCheck(null); setResult(null); setError("") }

    const handleFiles = (e) => {
        const picked = Array.from(e.target.files || [])
        e.target.value = ""
        if (picked.length === 0) return
        resetResults()
        const bad = firstNonXlsx(picked)
        if (bad) {
            setFiles([])
            setError(`Yalnız .xlsx dosyası seçilebilir: ${bad}`)
            return
        }
        setFiles(picked)
    }

    const removeFile = (index) => {
        resetResults()
        setFiles(fs => fs.filter((_, i) => i !== index))
    }

    const sendCheck = useCallback(() => {
        if (!key || files.length === 0 || busy) return
        const id = ++runId.current
        const source = axios.CancelToken.source()
        cancelRef.current = source
        resetResults()
        setBusy("check")
        CoursePlanService.importCoursePlans(key, files, true, source.token)
            .then(r => { if (id === runId.current && mounted.current) { setCheck(r.data); setResult({ data: r.data, saved: false }) } })
            .catch(err => {
                if (axios.isCancel(err) || id !== runId.current || !mounted.current) return
                setError(importErrorText(err, "check"))
                if (err.response && err.response.status === 403) focusKey.current = true
            })
            .finally(() => { if (id === runId.current && mounted.current) { setBusy(""); cancelRef.current = null } })
    }, [key, files, busy])

    const cancelCheck = () => {
        runId.current++
        if (cancelRef.current) cancelRef.current.cancel()
        cancelRef.current = null
        setBusy("")
    }

    const handleSubmit = (e) => {
        e.preventDefault()   // Enter yalnız Kontrol Et'i çalıştırır
        sendCheck()
    }

    const sendSave = () => {
        if (!check || busy) return
        const id = ++runId.current
        setBusy("save")
        setError("")
        CoursePlanService.importCoursePlans(key, files, false)
            .then(r => {
                if (id !== runId.current || !mounted.current) return
                setResult({ data: r.data, saved: true })
                setCheck(null)
                setFiles([])
                if (onSaved) onSaved()
            })
            .catch(err => {
                if (id !== runId.current || !mounted.current) return
                setError(importErrorText(err, "save"))
                if (err.response && err.response.status === 403) {
                    focusKey.current = true   // kontrol kalır
                } else {
                    setCheck(null)
                    setResult(null)
                }
            })
            .finally(() => { if (id === runId.current && mounted.current) setBusy("") })
    }

    const planCount = check ? summarizeImport(check).plans : 0
    const checking = busy === "check"
    const saving = busy === "save"

    return (
        <Accordion expanded={expanded} onChange={(e, v) => setExpanded(v)} className="section-card curricula-import">
            <AccordionSummary expandIcon={<ExpandMoreIcon/>} aria-controls="curricula-import-body"
                              id="curricula-import-header">
                <Typography variant="h6" component="h2">Müfredat Dosyası İçe Aktar</Typography>
            </AccordionSummary>
            <AccordionDetails id="curricula-import-body" className="import-body">
                <Typography variant="body2" className="muted">
                    Önce Kontrol Et'e basın; bu adım hiçbir şeyi kaydetmez. Kaydederken aynı müfredat varsa ders listesi
                    dosyadakiyle değiştirilir. Dosyalarda olmayan müfredatlara dokunulmaz.
                </Typography>
                <form className="import-form" onSubmit={handleSubmit}>
                    <TextField label="Yönetici Anahtarı" type="password" value={key} size="small" variant="outlined"
                               inputRef={keyRef} autoComplete="off" disabled={!!busy}
                               onChange={e => setKey(e.target.value)}/>
                    <Button variant="outlined" component="label" disabled={!!busy}>
                        Excel Dosyaları Seç
                        <input type="file" accept=".xlsx" multiple hidden onChange={handleFiles}/>
                    </Button>
                    <Button type="submit" variant="contained" color="primary"
                            disabled={!key || files.length === 0 || !!busy}
                            startIcon={checking ? <CircularProgress size={16} color="inherit"/> : null}>
                        {checking ? "Kontrol ediliyor…" : "Kontrol Et"}
                    </Button>
                    {checking && <Button variant="text" onClick={cancelCheck}>Vazgeç</Button>}
                    <Button type="button" variant="contained" color="secondary"
                            disabled={!check || planCount === 0 || !!busy} onClick={sendSave}
                            startIcon={saving ? <CircularProgress size={16} color="inherit"/> : null}>
                        {saving ? "Kaydediliyor…" : (check && planCount > 0 ? `${planCount} müfredatı kaydet` : "Kaydet")}
                    </Button>
                </form>
                {saving && <Typography variant="body2" role="status">Kaydediliyor… Sayfayı kapatmayın.</Typography>}
                {files.length > 0 && (
                    <div className="file-chips">
                        {files.map((f, i) => (
                            <Chip key={`${f.name}-${i}`} label={f.name} className="file-chip" disabled={!!busy}
                                  onDelete={() => removeFile(i)}
                                  deleteIcon={<CancelIcon aria-label={`${f.name} dosyasını çıkar`} role="img"/>}/>
                        ))}
                    </div>
                )}
                {error && <Alert severity="error" role="alert">{error}</Alert>}
                <div aria-live="polite">
                    {result && <ImportSummary key={result.saved ? "saved" : "check"} data={result.data} saved={result.saved}/>}
                </div>
            </AccordionDetails>
        </Accordion>
    )
}

export default CurriculaImport
