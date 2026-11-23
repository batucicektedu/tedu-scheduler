import React, { useCallback, useEffect, useRef, useState } from "react";
import { Typography } from "@material-ui/core";
import { CoursePlanService } from "../../service";
import PageNav from "../PageNav/PageNav";
import CurriculaImport from "./CurriculaImport";
import CurriculaList from "./CurriculaList";
import CurriculaDetail from "./CurriculaDetail";
import "../StudentsPage/StudentsPage.scss";
import "./CurriculaPage.scss";

const CurriculaPage = () => {
    const [plans, setPlans] = useState([])
    const [status, setStatus] = useState("loading")
    const [autoOpen, setAutoOpen] = useState(undefined)
    const [selectedId, setSelectedId] = useState(null)
    const [reloadKey, setReloadKey] = useState(0)
    const listRef = useRef(null)
    const listReq = useRef(0)
    const decided = useRef(false)

    const loadList = useCallback((silent) => {
        const mine = ++listReq.current
        if (!silent) setStatus("loading")
        CoursePlanService.getCoursePlans()
            .then(r => {
                if (mine !== listReq.current) return
                const data = Array.isArray(r.data) ? r.data : []
                setPlans(data)
                setStatus("ok")
                if (!decided.current) { decided.current = true; setAutoOpen(data.length === 0) }
            })
            .catch(() => { if (mine === listReq.current) setStatus("error") })
    }, [])

    useEffect(() => { loadList(false); return () => { listReq.current++ } }, [loadList])

    const handleSaved = useCallback(() => {
        loadList(true)
        setReloadKey(k => k + 1)
    }, [loadList])

    const handleBack = () => {
        const id = selectedId
        setSelectedId(null)
        // Kart kalkınca satırın düğmesine dön.
        setTimeout(() => { if (listRef.current) listRef.current.focusRow(id) }, 0)
    }

    const selectedPlan = plans.find(p => p.id === selectedId)

    return (
        <div className="students-page curricula-page">
            <div className="students-container">
                <div className="page-header">
                    <Typography variant="h4" component="h1">Müfredatlar</Typography>
                    <PageNav/>
                </div>

                <CurriculaImport autoOpen={autoOpen} onSaved={handleSaved}/>

                <CurriculaList ref={listRef} plans={plans} status={status} onRetry={() => loadList(false)}
                               selectedId={selectedId} onSelect={setSelectedId}/>

                {selectedId != null && (
                    <CurriculaDetail id={selectedId} listPlan={selectedPlan} reloadKey={reloadKey} onBack={handleBack}/>
                )}
            </div>
        </div>
    )
}

export default CurriculaPage
