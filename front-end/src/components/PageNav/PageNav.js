import React from "react";
import { Link as RouterLink, useLocation } from "react-router-dom";
import { Link } from "@material-ui/core";
import "./PageNav.scss";

const PAGES = [
    { to: "/", label: "Haftalık Program" },
    { to: "/students", label: "Öğrenci Yönetimi" },
    { to: "/curricula", label: "Müfredatlar" },
]

// Sayfalar arası küçük gezinme satırı; bulunulan sayfa kalın ve aria-current="page".
const PageNav = () => {
    const { pathname } = useLocation()
    return (
        <nav aria-label="Sayfalar" className="page-nav">
            {PAGES.map(p => {
                const current = pathname === p.to
                return (
                    <Link key={p.to} component={RouterLink} to={p.to}
                          className={current ? "page-nav-link current" : "page-nav-link"}
                          aria-current={current ? "page" : undefined}>
                        {p.label}
                    </Link>
                )
            })}
        </nav>
    )
}

export default PageNav
