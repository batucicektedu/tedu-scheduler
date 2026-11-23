import React, { useEffect, useRef, useState } from "react";
import {
    Button, Chip, CircularProgress, Paper, Table, TableBody, TableCell, TableContainer, TableHead,
    TableRow, Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import axios from "axios";
import { CoursePlanService } from "../../service";
import {
    detailErrorText, formatDateTime, formatNumber, hasNoItems, isElective, itemCodeText, itemNameText,
    planTitle, semesterLabel,
} from "./curriculaUtils";

const prefersReducedMotion = () =>
    typeof window !== "undefined" && window.matchMedia
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches

const SemesterTable = ({ sem }) => (
    <div className="semester-block">
        <Typography variant="subtitle1" component="h3" className="semester-title">{semesterLabel(sem.semester)}</Typography>
        <TableContainer>
            <Table size="small" aria-label={semesterLabel(sem.semester)}>
                <TableHead>
                    <TableRow>
                        <TableCell className="hide-xs">Kod</TableCell>
                        <TableCell>Ders</TableCell>
                        <TableCell className="hide-xs" align="right">T</TableCell>
                        <TableCell className="hide-xs" align="right">P</TableCell>
                        <TableCell className="hide-xs" align="right">L</TableCell>
                        <TableCell align="right">K</TableCell>
                        <TableCell align="right">AKTS</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {(sem.items || []).map((it, i) => {
                        const el = isElective(it)
                        return (
                            <TableRow key={i} className={el ? "elective-row" : undefined}>
                                <TableCell className="hide-xs nowrap">{itemCodeText(it)}</TableCell>
                                <TableCell>
                                    <span className="only-xs muted small block">{itemCodeText(it)}</span>
                                    <span className={el ? "elective-name wrap-text" : "wrap-text"}>{itemNameText(it)}</span>
                                    {el && <Chip size="small" variant="outlined" label="Seçmeli" className="elective-chip"/>}
                                    <span className="only-xs muted small block">
                                        {`T ${formatNumber(it.theory)} · P ${formatNumber(it.practice)} · L ${formatNumber(it.lab)}`}
                                    </span>
                                </TableCell>
                                <TableCell className="hide-xs" align="right">{formatNumber(it.theory)}</TableCell>
                                <TableCell className="hide-xs" align="right">{formatNumber(it.practice)}</TableCell>
                                <TableCell className="hide-xs" align="right">{formatNumber(it.lab)}</TableCell>
                                <TableCell align="right">{formatNumber(it.credits)}</TableCell>
                                <TableCell align="right">{formatNumber(it.ects)}</TableCell>
                            </TableRow>
                        )
                    })}
                    <TableRow className="total-row">
                        <TableCell className="hide-xs" colSpan={5}>Yarıyıl toplamı</TableCell>
                        <TableCell className="only-xs-cell">Yarıyıl toplamı</TableCell>
                        <TableCell align="right">{formatNumber(sem.totalCredits)}</TableCell>
                        <TableCell align="right">{formatNumber(sem.totalEcts)}</TableCell>
                    </TableRow>
                </TableBody>
            </Table>
        </TableContainer>
    </div>
)

// id: seçili müfredat; listPlan: listedeki satır (başlık yüklenirken kullanılır); reloadKey: kayıttan sonra yenileme.
const CurriculaDetail = ({ id, listPlan, reloadKey, onBack }) => {
    const [state, setState] = useState({ status: "loading", data: null, error: "" })
    const [attempt, setAttempt] = useState(0)
    const cardRef = useRef(null)
    const headingRef = useRef(null)
    const reqId = useRef(0)

    // Seçim değişince karta kaydır, odağı başlığa ver.
    useEffect(() => {
        if (cardRef.current && cardRef.current.scrollIntoView) {
            cardRef.current.scrollIntoView({ behavior: prefersReducedMotion() ? "auto" : "smooth", block: "start" })
        }
        if (headingRef.current) headingRef.current.focus({ preventScroll: true })
    }, [id])

    useEffect(() => {
        const mine = ++reqId.current
        setState(s => (s.data && s.data.plan && s.data.plan.id === id
            ? s : { status: "loading", data: null, error: "" }))
        CoursePlanService.getCoursePlan(id)
            .then(r => { if (mine === reqId.current) setState({ status: "ok", data: r.data, error: "" }) })
            .catch(err => {
                if (mine !== reqId.current || axios.isCancel(err)) return
                setState({ status: "error", data: null, error: detailErrorText(err), notFound: !!(err.response && err.response.status === 404) })
            })
        return () => { reqId.current++ }
    }, [id, reloadKey, attempt])

    const plan = (state.data && state.data.plan) || listPlan
    const semesters = state.data && state.data.semesters
        ? [...state.data.semesters].sort((a, b) => a.semester - b.semester) : []
    const empty = state.status === "ok" && hasNoItems(state.data.plan)
    const imported = plan ? formatDateTime(plan.importedAt) : ""

    return (
        <Paper className="section-card" ref={cardRef} aria-busy={state.status === "loading"}>
            <div className="detail-head">
                <Typography variant="h6" component="h2" tabIndex={-1} ref={headingRef} className="detail-title wrap-text">
                    {planTitle(plan)}
                </Typography>
                <Button variant="outlined" size="small" onClick={onBack}>Listeye dön</Button>
            </div>

            {state.status === "loading" && <div className="center-box"><CircularProgress aria-label="Ayrıntı yükleniyor"/></div>}
            {state.status === "error" && (
                <Alert severity="error" role="alert"
                       action={state.notFound ? null
                           : <Button color="inherit" size="small" onClick={() => setAttempt(a => a + 1)}>Yeniden dene</Button>}>
                    {state.error}
                </Alert>
            )}
            {state.status === "ok" && (
                <>
                    {!empty && (
                        <div className="summary-chips">
                            <Chip label={`Yarıyıl: ${semesters.length}`}/>
                            <Chip label={`Ders: ${state.data.plan.courseCount}`}/>
                            <Chip label={`Seçmeli: ${state.data.plan.electiveCount}`}/>
                            <Chip label={`Kredi: ${formatNumber(state.data.plan.totalCredits)}`}/>
                            <Chip label={`AKTS: ${formatNumber(state.data.plan.totalEcts)}`}/>
                        </div>
                    )}
                    {plan.sourceFile && (
                        <Typography variant="body2" className="muted wrap-text">
                            {`Kaynak: ${plan.sourceFile}${imported ? ` · Aktarıldı: ${imported}` : ""}`}
                        </Typography>
                    )}
                    {empty ? (
                        <Alert severity="info" className="detail-info">
                            {plan.kind === "MINOR"
                                ? "Bu yandalın ders listesi dosyada yok. Dosyada yalnız programın adı ve müfredat yılı yer alıyor."
                                : "Bu müfredatın ders listesi dosyada yok."}
                        </Alert>
                    ) : (
                        <>
                            <Typography variant="body2" className="muted legend">
                                T: teori · P: uygulama · L: laboratuvar · K: kredi
                            </Typography>
                            {semesters.map(s => <SemesterTable key={s.semester} sem={s}/>)}
                            <div className="grand-total">
                                <Typography component="span">Genel toplam</Typography>
                                <Typography component="span">{`K: ${formatNumber(state.data.plan.totalCredits)}`}</Typography>
                                <Typography component="span">{`AKTS: ${formatNumber(state.data.plan.totalEcts)}`}</Typography>
                            </div>
                        </>
                    )}
                </>
            )}
        </Paper>
    )
}

export default CurriculaDetail
