import React from "react";
import { Link as RouterLink, useLocation } from "react-router-dom";
import { Button } from "@material-ui/core";
import { People, MenuBook, Event } from "@material-ui/icons";
import { PAGES } from "./PageNav";
import "./PageNav.scss";

const ICONS = {
    "/": Event,
    "/students": People,
    "/curricula": MenuBook,
}

// Ana ekranın altındaki gezinme butonları; bulunulan sayfa dışındakileri gösterir.
const PageButtons = () => {
    const { pathname } = useLocation()
    return (
        <nav aria-label="Sayfalar" className="page-buttons">
            {PAGES.filter(p => p.to !== pathname).map(p => {
                const Icon = ICONS[p.to]
                return (
                    <Button key={p.to} component={RouterLink} to={p.to} variant="outlined"
                            className="page-button" onClick={() => window.scrollTo(0, 0)}
                            startIcon={Icon && <Icon/>}>
                        {p.label}
                    </Button>
                )
            })}
        </nav>
    )
}

export default PageButtons
