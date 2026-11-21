///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

export type Locale = "en" | "es" | "fr" | "de" | "ja";

export const LOCALES: readonly Locale[] = ["en", "es", "fr", "de", "ja"];

export const LOCALE_LABELS: Record<Locale, string> = {
    en: "English",
    es: "Español",
    fr: "Français",
    de: "Deutsch",
    ja: "日本語",
};

type Catalog = Record<string, string>;

const en: Catalog = {
    "nav.files": "Files",
    "nav.editor": "Editor",
    "nav.run": "Run",
    "nav.languages": "Languages",
    "nav.debug": "Debug",
    "nav.git": "Git",
    "nav.terminal": "Terminal",
    "nav.preview": "Preview",
    "bar.back": "Back to workspaces",
    "bar.checkPorts": "Check ports",
    "bar.startWorkspace": "Start workspace",
    "bar.workspaces": "Workspaces",
    "bar.theme": "Theme",
    "bar.language": "Language",
    "home.eyebrow": "Your Linux workspaces",
    "home.title": "Make room",
    "home.titleEm": "to build.",
    "home.intro":
        "A focused development environment, running on your server and ready from any browser.",
    "home.projectSpaces": "Project spaces",
    "home.createTitle": "Create a workspace",
    "home.createCopy": "A clean start",
    "home.workspaceName": "Workspace name",
    "home.starter": "Starter",
    "home.createAction": "Create workspace",
    "home.samples": "Sample projects",
    "home.samplesCopy": "Start from a bundled template.",
    "common.save": "Save",
    "common.cancel": "Cancel",
    "common.retry": "Retry",
    "common.stop": "Stop",
    "common.refresh": "Refresh",
    "common.saved": "Saved",
    "common.unsaved": "Unsaved changes, saved locally",
    "files.explorer": "Explorer",
    "files.newFile": "New file",
    "files.newFolder": "New folder",
    "files.download": "Download",
    "files.rename": "Rename",
    "files.delete": "Delete",
    "files.empty": "This workspace is empty.",
    "files.search": "Search workspace…",
    "git.heading": "Source control",
    "git.stage": "Stage",
    "git.unstage": "Unstage",
    "git.commit": "Commit",
    "git.message": "Commit message",
    "git.clean": "Working tree clean.",
    "git.history": "Commit history",
    "git.branches": "Branches",
    "git.blame": "Blame",
    "git.diffSideBySide": "Side-by-side diff",
    "git.unavailable": "Unavailable on this host.",
    "debug.breakpoints": "Breakpoints",
    "debug.condition": "Condition",
    "debug.logpoint": "Logpoint",
    "debug.setBreakpoints": "Set",
    "debug.clear": "Clear",
    "debug.addCondition": "Add condition",
    "debug.addLogpoint": "Add logpoint",
    "bulk.select": "Select",
    "bulk.copy": "Copy",
    "bulk.move": "Move",
    "bulk.delete": "Delete selected",
    "bulk.zip": "Download as ZIP",
    "bulk.undo": "Undo",
    "bulk.selected": "{count, plural, one {# file selected} other {# files selected}}",
    "upload.drop": "Drop files to upload",
    "upload.paste": "Paste images or text",
    "upload.limit": "File too large or unsupported type.",
    "markdown.preview": "Preview",
    "markdown.split": "Split preview",
    "markdown.empty": "Nothing to preview.",
    "tour.next": "Next",
    "tour.skip": "Skip tour",
    "tour.finish": "Finish",
    "tour.step1Title": "Create a workspace",
    "tour.step1Body": "Pick a starter and name your first workspace.",
    "tour.step2Title": "Open a file",
    "tour.step2Body": "Browse the explorer and open a file to edit.",
    "tour.step3Title": "Run a task",
    "tour.step3Body": "Start a task and watch its output.",
    "tour.step4Title": "Preview",
    "tour.step4Body": "Register a listening port to see your app.",
    "tour.progress": "Step {current} of {total}",
    "panel.collaboration": "Collaborate",
    "panel.agent": "Agent",
    "panel.data": "Data",
    "panel.club": "Club",
    "panel.ops": "Ops",
    "panel.history": "History",
    "panel.trash": "Trash",
    "panel.diff": "Diff",
    "panel.clone": "Clone",
    "panel.config": "Config",
    "panel.flags": "Flags",
    "bar.tools": "Workspace tools",
    "bar.signIn": "Sign in",
    "service.notLoaded": "Not loaded",
    "service.unavailable": "Unavailable on this host",
    "service.collaboration": "Collaboration",
    "service.agent": "Agent",
    "service.data": "Data tools",
    "service.club": "Club",
    "service.ops": "Admin ops",
    "service.collaboration.intro":
        "Members, presence, comments, live sessions and share links for this workspace.",
    "service.agent.intro": "Sessions, prompt snippets, staged patches, chat and model usage.",
    "service.data.intro": "SQLite browsing, CSV tables, notebooks, search index and file history.",
    "service.club.intro": "Instructor sessions, tutorials, checkpoints, showcase and moderation.",
    "service.ops.intro": "Support bundles, backups, audit, GDPR requests and approvals.",
    "home.emptyTitle": "Your next project starts here.",
    "home.emptyCopy": "Choose a starter and Jahaziel will lay down the first files.",
    "home.openWorkspace": "Open workspace",
    "home.clone": "Clone",
    "home.start": "Start",
    "plugins.heading": "Capabilities & plugins",
    "plugins.available": "Available",
    "plugins.unavailable": "Unavailable on this host",
    "plugins.loaded": "Loaded",
    "plugins.builtin": "Built-in",
};

const es: Catalog = {
    "nav.files": "Archivos",
    "nav.editor": "Editor",
    "nav.run": "Ejecutar",
    "nav.languages": "Lenguajes",
    "nav.debug": "Depurar",
    "nav.git": "Git",
    "nav.terminal": "Terminal",
    "nav.preview": "Vista previa",
    "bar.back": "Volver a espacios de trabajo",
    "bar.checkPorts": "Comprobar puertos",
    "bar.startWorkspace": "Iniciar espacio de trabajo",
    "bar.workspaces": "Espacios de trabajo",
    "bar.theme": "Tema",
    "bar.language": "Idioma",
    "home.eyebrow": "Tus espacios de trabajo Linux",
    "home.title": "Haz espacio",
    "home.titleEm": "para crear.",
    "home.intro":
        "Un entorno de desarrollo centrado, en tu servidor y listo desde cualquier navegador.",
    "home.projectSpaces": "Espacios de proyecto",
    "home.createTitle": "Crear un espacio de trabajo",
    "home.createCopy": "Un comienzo limpio",
    "home.workspaceName": "Nombre del espacio de trabajo",
    "home.starter": "Plantilla",
    "home.createAction": "Crear espacio de trabajo",
    "home.samples": "Proyectos de ejemplo",
    "home.samplesCopy": "Empieza con una plantilla incluida.",
    "common.save": "Guardar",
    "common.cancel": "Cancelar",
    "common.retry": "Reintentar",
    "common.stop": "Detener",
    "common.refresh": "Actualizar",
    "common.saved": "Guardado",
    "common.unsaved": "Cambios sin guardar, guardados localmente",
    "files.explorer": "Explorador",
    "files.newFile": "Nuevo archivo",
    "files.newFolder": "Nueva carpeta",
    "files.download": "Descargar",
    "files.rename": "Renombrar",
    "files.delete": "Eliminar",
    "files.empty": "Este espacio de trabajo está vacío.",
    "files.search": "Buscar en el espacio…",
    "git.heading": "Control de código",
    "git.stage": "Añadir",
    "git.unstage": "Quitar",
    "git.commit": "Confirmar",
    "git.message": "Mensaje de confirmación",
    "git.clean": "Árbol de trabajo limpio.",
    "git.history": "Historial de confirmaciones",
    "git.branches": "Ramas",
    "git.blame": "Blame",
    "git.diffSideBySide": "Diff lado a lado",
    "git.unavailable": "No disponible en este servidor.",
    "debug.breakpoints": "Puntos de ruptura",
    "debug.condition": "Condición",
    "debug.logpoint": "Punto de registro",
    "debug.setBreakpoints": "Establecer",
    "debug.clear": "Borrar",
    "debug.addCondition": "Añadir condición",
    "debug.addLogpoint": "Añadir punto de registro",
    "bulk.select": "Seleccionar",
    "bulk.copy": "Copiar",
    "bulk.move": "Mover",
    "bulk.delete": "Eliminar seleccionados",
    "bulk.zip": "Descargar como ZIP",
    "bulk.undo": "Deshacer",
    "bulk.selected":
        "{count, plural, one {# archivo seleccionado} other {# archivos seleccionados}}",
    "upload.drop": "Suelta archivos para subir",
    "upload.paste": "Pega imágenes o texto",
    "upload.limit": "Archivo demasiado grande o tipo no admitido.",
    "markdown.preview": "Vista previa",
    "markdown.split": "Vista dividida",
    "markdown.empty": "Nada que previsualizar.",
    "tour.next": "Siguiente",
    "tour.skip": "Omitir tour",
    "tour.finish": "Finalizar",
    "tour.step1Title": "Crea un espacio de trabajo",
    "tour.step1Body": "Elige una plantilla y nombra tu primer espacio de trabajo.",
    "tour.step2Title": "Abre un archivo",
    "tour.step2Body": "Explora el árbol y abre un archivo para editarlo.",
    "tour.step3Title": "Ejecuta una tarea",
    "tour.step3Body": "Inicia una tarea y observa su salida.",
    "tour.step4Title": "Vista previa",
    "tour.step4Body": "Registra un puerto activo para ver tu aplicación.",
    "tour.progress": "Paso {current} de {total}",
    "panel.collaboration": "Colaborar",
    "panel.agent": "Agente",
    "panel.data": "Datos",
    "panel.club": "Club",
    "panel.ops": "Operaciones",
    "panel.history": "Historial",
    "panel.trash": "Papelera",
    "panel.diff": "Diff",
    "panel.clone": "Clonar",
    "panel.config": "Configuración",
    "panel.flags": "Banderas",
    "bar.tools": "Herramientas del espacio de trabajo",
    "bar.signIn": "Iniciar sesión",
    "service.notLoaded": "No cargado",
    "service.unavailable": "No disponible en este servidor",
    "service.collaboration": "Colaboración",
    "service.agent": "Agente",
    "service.data": "Herramientas de datos",
    "service.club": "Club",
    "service.ops": "Operaciones de administración",
    "service.collaboration.intro":
        "Miembros, presencia, comentarios, sesiones en vivo y enlaces compartidos para este espacio de trabajo.",
    "service.agent.intro":
        "Sesiones, fragmentos de prompts, parches preparados, chat y uso del modelo.",
    "service.data.intro":
        "Exploración SQLite, tablas CSV, cuadernos, índice de búsqueda e historial de archivos.",
    "service.club.intro":
        "Sesiones de instructor, tutoriales, puntos de control, vitrina y moderación.",
    "service.ops.intro":
        "Paquetes de soporte, copias de seguridad, auditoría, solicitudes GDPR y aprobaciones.",
    "home.emptyTitle": "Tu próximo proyecto empieza aquí.",
    "home.emptyCopy": "Elige una plantilla y Jahaziel creará los primeros archivos.",
    "home.openWorkspace": "Abrir espacio de trabajo",
    "home.clone": "Clonar",
    "home.start": "Iniciar",
    "plugins.heading": "Capacidades y plugins",
    "plugins.available": "Disponible",
    "plugins.unavailable": "No disponible en este servidor",
    "plugins.loaded": "Cargado",
    "plugins.builtin": "Integrado",
};

const fr: Catalog = {
    "nav.files": "Fichiers",
    "nav.editor": "Éditeur",
    "nav.run": "Exécuter",
    "nav.languages": "Langages",
    "nav.debug": "Déboguer",
    "nav.git": "Git",
    "nav.terminal": "Terminal",
    "nav.preview": "Aperçu",
    "bar.back": "Retour aux espaces de travail",
    "bar.checkPorts": "Vérifier les ports",
    "bar.startWorkspace": "Démarrer l'espace de travail",
    "bar.workspaces": "Espaces de travail",
    "bar.theme": "Thème",
    "bar.language": "Langue",
    "home.eyebrow": "Vos espaces de travail Linux",
    "home.title": "Faites place",
    "home.titleEm": "à créer.",
    "home.intro":
        "Un environnement de développement concentré, sur votre serveur et accessible depuis tout navigateur.",
    "home.projectSpaces": "Espaces de projet",
    "home.createTitle": "Créer un espace de travail",
    "home.createCopy": "Un départ propre",
    "home.workspaceName": "Nom de l'espace de travail",
    "home.starter": "Modèle",
    "home.createAction": "Créer l'espace de travail",
    "home.samples": "Projets d'exemple",
    "home.samplesCopy": "Commencez avec un modèle fourni.",
    "common.save": "Enregistrer",
    "common.cancel": "Annuler",
    "common.retry": "Réessayer",
    "common.stop": "Arrêter",
    "common.refresh": "Actualiser",
    "common.saved": "Enregistré",
    "common.unsaved": "Modifications non enregistrées, conservées localement",
    "files.explorer": "Explorateur",
    "files.newFile": "Nouveau fichier",
    "files.newFolder": "Nouveau dossier",
    "files.download": "Télécharger",
    "files.rename": "Renommer",
    "files.delete": "Supprimer",
    "files.empty": "Cet espace de travail est vide.",
    "files.search": "Rechercher dans l'espace…",
    "git.heading": "Contrôle de version",
    "git.stage": "Ajouter",
    "git.unstage": "Retirer",
    "git.commit": "Valider",
    "git.message": "Message de validation",
    "git.clean": "Arbre de travail propre.",
    "git.history": "Historique des validations",
    "git.branches": "Branches",
    "git.blame": "Blame",
    "git.diffSideBySide": "Diff côte à côte",
    "git.unavailable": "Indisponible sur cet hôte.",
    "debug.breakpoints": "Points d'arrêt",
    "debug.condition": "Condition",
    "debug.logpoint": "Point de journalisation",
    "debug.setBreakpoints": "Définir",
    "debug.clear": "Effacer",
    "debug.addCondition": "Ajouter une condition",
    "debug.addLogpoint": "Ajouter un point de journalisation",
    "bulk.select": "Sélectionner",
    "bulk.copy": "Copier",
    "bulk.move": "Déplacer",
    "bulk.delete": "Supprimer la sélection",
    "bulk.zip": "Télécharger en ZIP",
    "bulk.undo": "Annuler",
    "bulk.selected": "{count, plural, one {# fichier sélectionné} other {# fichiers sélectionnés}}",
    "upload.drop": "Déposez des fichiers à téléverser",
    "upload.paste": "Collez des images ou du texte",
    "upload.limit": "Fichier trop volumineux ou type non pris en charge.",
    "markdown.preview": "Aperçu",
    "markdown.split": "Aperçu fractionné",
    "markdown.empty": "Rien à prévisualiser.",
    "tour.next": "Suivant",
    "tour.skip": "Passer la visite",
    "tour.finish": "Terminer",
    "tour.step1Title": "Créez un espace de travail",
    "tour.step1Body": "Choisissez un modèle et nommez votre premier espace de travail.",
    "tour.step2Title": "Ouvrez un fichier",
    "tour.step2Body": "Parcourez l'explorateur et ouvrez un fichier à modifier.",
    "tour.step3Title": "Exécutez une tâche",
    "tour.step3Body": "Lancez une tâche et observez sa sortie.",
    "tour.step4Title": "Aperçu",
    "tour.step4Body": "Enregistrez un port à l'écoute pour voir votre application.",
    "tour.progress": "Étape {current} sur {total}",
    "panel.collaboration": "Collaborer",
    "panel.agent": "Agent",
    "panel.data": "Données",
    "panel.club": "Club",
    "panel.ops": "Ops",
    "panel.history": "Historique",
    "panel.trash": "Corbeille",
    "panel.diff": "Diff",
    "panel.clone": "Cloner",
    "panel.config": "Configuration",
    "panel.flags": "Drapeaux",
    "bar.tools": "Outils de l'espace de travail",
    "bar.signIn": "Se connecter",
    "service.notLoaded": "Non chargé",
    "service.unavailable": "Indisponible sur cet hôte",
    "service.collaboration": "Collaboration",
    "service.agent": "Agent",
    "service.data": "Outils de données",
    "service.club": "Club",
    "service.ops": "Administration",
    "service.collaboration.intro":
        "Membres, présence, commentaires, sessions en direct et liens de partage pour cet espace de travail.",
    "service.agent.intro":
        "Sessions, extraits de prompts, correctifs préparés, chat et utilisation du modèle.",
    "service.data.intro":
        "Exploration SQLite, tableaux CSV, notebooks, index de recherche et historique des fichiers.",
    "service.club.intro":
        "Sessions d'instructeur, tutoriels, points de contrôle, vitrine et modération.",
    "service.ops.intro": "Paquets d'assistance, sauvegardes, audit, demandes RGPD et approbations.",
    "home.emptyTitle": "Votre prochain projet commence ici.",
    "home.emptyCopy": "Choisissez un modèle et Jahaziel créera les premiers fichiers.",
    "home.openWorkspace": "Ouvrir l'espace de travail",
    "home.clone": "Cloner",
    "home.start": "Démarrer",
    "plugins.heading": "Capacités et plugins",
    "plugins.available": "Disponible",
    "plugins.unavailable": "Indisponible sur cet hôte",
    "plugins.loaded": "Chargé",
    "plugins.builtin": "Intégré",
};

const de: Catalog = {
    "nav.files": "Dateien",
    "nav.editor": "Editor",
    "nav.run": "Ausführen",
    "nav.languages": "Sprachen",
    "nav.debug": "Debuggen",
    "nav.git": "Git",
    "nav.terminal": "Terminal",
    "nav.preview": "Vorschau",
    "bar.back": "Zurück zu Arbeitsbereichen",
    "bar.checkPorts": "Ports prüfen",
    "bar.startWorkspace": "Arbeitsbereich starten",
    "bar.workspaces": "Arbeitsbereiche",
    "bar.theme": "Design",
    "bar.language": "Sprache",
    "home.eyebrow": "Ihre Linux-Arbeitsbereiche",
    "home.title": "Schaffen Sie Raum",
    "home.titleEm": "zum Bauen.",
    "home.intro":
        "Eine fokussierte Entwicklungsumgebung auf Ihrem Server, bereit in jedem Browser.",
    "home.projectSpaces": "Projektbereiche",
    "home.createTitle": "Arbeitsbereich erstellen",
    "home.createCopy": "Ein sauberer Start",
    "home.workspaceName": "Name des Arbeitsbereichs",
    "home.starter": "Vorlage",
    "home.createAction": "Arbeitsbereich erstellen",
    "home.samples": "Beispielprojekte",
    "home.samplesCopy": "Beginnen Sie mit einer mitgelieferten Vorlage.",
    "common.save": "Speichern",
    "common.cancel": "Abbrechen",
    "common.retry": "Erneut versuchen",
    "common.stop": "Stoppen",
    "common.refresh": "Aktualisieren",
    "common.saved": "Gespeichert",
    "common.unsaved": "Ungespeicherte Änderungen, lokal gesichert",
    "files.explorer": "Explorer",
    "files.newFile": "Neue Datei",
    "files.newFolder": "Neuer Ordner",
    "files.download": "Herunterladen",
    "files.rename": "Umbenennen",
    "files.delete": "Löschen",
    "files.empty": "Dieser Arbeitsbereich ist leer.",
    "files.search": "Arbeitsbereich durchsuchen…",
    "git.heading": "Versionskontrolle",
    "git.stage": "Hinzufügen",
    "git.unstage": "Entfernen",
    "git.commit": "Committen",
    "git.message": "Commit-Nachricht",
    "git.clean": "Arbeitsbaum sauber.",
    "git.history": "Commit-Verlauf",
    "git.branches": "Branches",
    "git.blame": "Blame",
    "git.diffSideBySide": "Diff nebeneinander",
    "git.unavailable": "Auf diesem Host nicht verfügbar.",
    "debug.breakpoints": "Haltepunkte",
    "debug.condition": "Bedingung",
    "debug.logpoint": "Logpunkt",
    "debug.setBreakpoints": "Setzen",
    "debug.clear": "Löschen",
    "debug.addCondition": "Bedingung hinzufügen",
    "debug.addLogpoint": "Logpunkt hinzufügen",
    "bulk.select": "Auswählen",
    "bulk.copy": "Kopieren",
    "bulk.move": "Verschieben",
    "bulk.delete": "Auswahl löschen",
    "bulk.zip": "Als ZIP herunterladen",
    "bulk.undo": "Rückgängig",
    "bulk.selected": "{count, plural, one {# Datei ausgewählt} other {# Dateien ausgewählt}}",
    "upload.drop": "Dateien zum Hochladen ablegen",
    "upload.paste": "Bilder oder Text einfügen",
    "upload.limit": "Datei zu groß oder nicht unterstützter Typ.",
    "markdown.preview": "Vorschau",
    "markdown.split": "Geteilte Vorschau",
    "markdown.empty": "Nichts zur Vorschau.",
    "tour.next": "Weiter",
    "tour.skip": "Tour überspringen",
    "tour.finish": "Abschließen",
    "tour.step1Title": "Arbeitsbereich erstellen",
    "tour.step1Body": "Wählen Sie eine Vorlage und benennen Sie Ihren ersten Arbeitsbereich.",
    "tour.step2Title": "Datei öffnen",
    "tour.step2Body": "Durchsuchen Sie den Explorer und öffnen Sie eine Datei.",
    "tour.step3Title": "Aufgabe ausführen",
    "tour.step3Body": "Starten Sie eine Aufgabe und verfolgen Sie die Ausgabe.",
    "tour.step4Title": "Vorschau",
    "tour.step4Body": "Registrieren Sie einen lauschenden Port für Ihre Anwendung.",
    "tour.progress": "Schritt {current} von {total}",
    "panel.collaboration": "Zusammenarbeit",
    "panel.agent": "Agent",
    "panel.data": "Daten",
    "panel.club": "Club",
    "panel.ops": "Ops",
    "panel.history": "Verlauf",
    "panel.trash": "Papierkorb",
    "panel.diff": "Diff",
    "panel.clone": "Klonen",
    "panel.config": "Konfiguration",
    "panel.flags": "Flags",
    "bar.tools": "Arbeitsbereich-Werkzeuge",
    "bar.signIn": "Anmelden",
    "service.notLoaded": "Nicht geladen",
    "service.unavailable": "Auf diesem Host nicht verfügbar",
    "service.collaboration": "Zusammenarbeit",
    "service.agent": "Agent",
    "service.data": "Datenwerkzeuge",
    "service.club": "Club",
    "service.ops": "Admin-Ops",
    "service.collaboration.intro":
        "Mitglieder, Präsenz, Kommentare, Live-Sitzungen und Freigabelinks für diesen Arbeitsbereich.",
    "service.agent.intro":
        "Sitzungen, Prompt-Snippets, vorgemerkte Patches, Chat und Modellnutzung.",
    "service.data.intro": "SQLite-Browsing, CSV-Tabellen, Notebooks, Suchindex und Dateiverlauf.",
    "service.club.intro": "Kursleitersitzungen, Tutorials, Checkpoints, Showcase und Moderation.",
    "service.ops.intro": "Support-Pakete, Backups, Audit, DSGVO-Anfragen und Freigaben.",
    "home.emptyTitle": "Ihr nächstes Projekt beginnt hier.",
    "home.emptyCopy": "Wählen Sie eine Vorlage, und Jahaziel legt die ersten Dateien an.",
    "home.openWorkspace": "Arbeitsbereich öffnen",
    "home.clone": "Klonen",
    "home.start": "Starten",
    "plugins.heading": "Funktionen & Plugins",
    "plugins.available": "Verfügbar",
    "plugins.unavailable": "Auf diesem Host nicht verfügbar",
    "plugins.loaded": "Geladen",
    "plugins.builtin": "Integriert",
};

const ja: Catalog = {
    "nav.files": "ファイル",
    "nav.editor": "エディター",
    "nav.run": "実行",
    "nav.languages": "言語",
    "nav.debug": "デバッグ",
    "nav.git": "Git",
    "nav.terminal": "ターミナル",
    "nav.preview": "プレビュー",
    "bar.back": "ワークスペースに戻る",
    "bar.checkPorts": "ポートを確認",
    "bar.startWorkspace": "ワークスペースを開始",
    "bar.workspaces": "ワークスペース",
    "bar.theme": "テーマ",
    "bar.language": "言語",
    "home.eyebrow": "Linux ワークスペース",
    "home.title": "場所をつくる",
    "home.titleEm": "つくるための。",
    "home.intro": "サーバー上で動作し、どのブラウザーからでも使える集中開発環境。",
    "home.projectSpaces": "プロジェクトスペース",
    "home.createTitle": "ワークスペースを作成",
    "home.createCopy": "すっきりした始まり",
    "home.workspaceName": "ワークスペース名",
    "home.starter": "スターター",
    "home.createAction": "ワークスペースを作成",
    "home.samples": "サンプルプロジェクト",
    "home.samplesCopy": "同梱のテンプレートから始めましょう。",
    "common.save": "保存",
    "common.cancel": "キャンセル",
    "common.retry": "再試行",
    "common.stop": "停止",
    "common.refresh": "更新",
    "common.saved": "保存済み",
    "common.unsaved": "未保存の変更、ローカルに保存済み",
    "files.explorer": "エクスプローラー",
    "files.newFile": "新しいファイル",
    "files.newFolder": "新しいフォルダー",
    "files.download": "ダウンロード",
    "files.rename": "名前を変更",
    "files.delete": "削除",
    "files.empty": "このワークスペースは空です。",
    "files.search": "ワークスペースを検索…",
    "git.heading": "ソース管理",
    "git.stage": "ステージ",
    "git.unstage": "アンステージ",
    "git.commit": "コミット",
    "git.message": "コミットメッセージ",
    "git.clean": "作業ツリーはクリーンです。",
    "git.history": "コミット履歴",
    "git.branches": "ブランチ",
    "git.blame": "ブレイム",
    "git.diffSideBySide": "左右比較 diff",
    "git.unavailable": "このホストでは利用できません。",
    "debug.breakpoints": "ブレークポイント",
    "debug.condition": "条件",
    "debug.logpoint": "ログポイント",
    "debug.setBreakpoints": "設定",
    "debug.clear": "クリア",
    "debug.addCondition": "条件を追加",
    "debug.addLogpoint": "ログポイントを追加",
    "bulk.select": "選択",
    "bulk.copy": "コピー",
    "bulk.move": "移動",
    "bulk.delete": "選択を削除",
    "bulk.zip": "ZIP でダウンロード",
    "bulk.undo": "元に戻す",
    "bulk.selected": "{count, plural, other {# 個のファイルを選択中}}",
    "upload.drop": "ファイルをドロップしてアップロード",
    "upload.paste": "画像やテキストを貼り付け",
    "upload.limit": "ファイルが大きすぎるか、対応していない形式です。",
    "markdown.preview": "プレビュー",
    "markdown.split": "分割プレビュー",
    "markdown.empty": "プレビューするものがありません。",
    "tour.next": "次へ",
    "tour.skip": "ツアーをスキップ",
    "tour.finish": "完了",
    "tour.step1Title": "ワークスペースを作成",
    "tour.step1Body": "テンプレートを選び、最初のワークスペースに名前を付けましょう。",
    "tour.step2Title": "ファイルを開く",
    "tour.step2Body": "エクスプローラーを参照してファイルを開きましょう。",
    "tour.step3Title": "タスクを実行",
    "tour.step3Body": "タスクを開始して出力を確認しましょう。",
    "tour.step4Title": "プレビュー",
    "tour.step4Body": "待機中のポートを登録してアプリを表示しましょう。",
    "tour.progress": "ステップ {current} / {total}",
    "panel.collaboration": "共同作業",
    "panel.agent": "エージェント",
    "panel.data": "データ",
    "panel.club": "クラブ",
    "panel.ops": "運用",
    "panel.history": "履歴",
    "panel.trash": "ごみ箱",
    "panel.diff": "差分",
    "panel.clone": "複製",
    "panel.config": "設定",
    "panel.flags": "フラグ",
    "bar.tools": "ワークスペースのツール",
    "bar.signIn": "サインイン",
    "service.notLoaded": "未読み込み",
    "service.unavailable": "このホストでは利用不可",
    "service.collaboration": "共同作業",
    "service.agent": "エージェント",
    "service.data": "データツール",
    "service.club": "クラブ",
    "service.ops": "管理運用",
    "service.collaboration.intro":
        "このワークスペースのメンバー、プレゼンス、コメント、ライブセッション、共有リンク。",
    "service.agent.intro":
        "セッション、プロンプト集、ステージ済みパッチ、チャット、モデル使用状況。",
    "service.data.intro":
        "SQLite ブラウズ、CSV テーブル、ノートブック、検索インデックス、ファイル履歴。",
    "service.club.intro":
        "インストラクターセッション、チュートリアル、チェックポイント、ショーケース、モデレーション。",
    "service.ops.intro": "サポートバンドル、バックアップ、監査、GDPR リクエスト、承認。",
    "home.emptyTitle": "次のプロジェクトはここから始まります。",
    "home.emptyCopy": "スターターを選ぶと、Jahaziel が最初のファイルを用意します。",
    "home.openWorkspace": "ワークスペースを開く",
    "home.clone": "複製",
    "home.start": "開始",
    "plugins.heading": "機能とプラグイン",
    "plugins.available": "利用可能",
    "plugins.unavailable": "このホストでは利用不可",
    "plugins.loaded": "読み込み済み",
    "plugins.builtin": "組み込み",
};

const catalogs: Record<Locale, Catalog> = { en, es, fr, de, ja };

const STORAGE_KEY = "jahaziel:locale";
const listeners = new Set<() => void>();

function readStoredLocale(): Locale {
    try {
        const value = localStorage.getItem(STORAGE_KEY);
        if (value && LOCALES.includes(value as Locale)) return value as Locale;
    } catch {
        /* Storage can be disabled. */
    }
    return "en";
}

let current: Locale = readStoredLocale();

function applyDocumentLocale(locale: Locale): void {
    if (typeof document === "undefined") return;
    document.documentElement.lang = locale;
    document.documentElement.dir = "ltr";
}

applyDocumentLocale(current);

export function getLocale(): Locale {
    return current;
}

export function setLocale(locale: Locale): void {
    if (!LOCALES.includes(locale) || locale === current) return;
    current = locale;
    try {
        localStorage.setItem(STORAGE_KEY, locale);
    } catch {
        /* Storage can be disabled. */
    }
    applyDocumentLocale(locale);
    for (const listener of listeners) listener();
}

export function subscribeLocale(listener: () => void): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

function findClosing(template: string, open: number): number {
    let depth = 0;
    for (let index = open; index < template.length; index += 1) {
        const character = template[index];
        if (character === "{") depth += 1;
        else if (character === "}") {
            depth -= 1;
            if (depth === 0) return index;
        }
    }
    return -1;
}

function pluralCategory(locale: Locale, count: number): string {
    if (locale === "ja") return "other";
    if (locale === "fr") return count >= 0 && count < 2 ? "one" : "other";
    return count === 1 ? "one" : "other";
}

function formatPlural(
    options: string,
    count: number,
    locale: Locale,
    params: Record<string, string | number>,
): string {
    const branches = new Map<string, string>();
    let cursor = 0;
    while (cursor < options.length) {
        while (cursor < options.length && /\s/.test(options[cursor])) cursor += 1;
        const start = cursor;
        while (cursor < options.length && !/\s/.test(options[cursor]) && options[cursor] !== "{")
            cursor += 1;
        const selector = options.slice(start, cursor);
        while (cursor < options.length && /\s/.test(options[cursor])) cursor += 1;
        if (options[cursor] !== "{") break;
        const close = findClosing(options, cursor);
        if (close < 0) break;
        branches.set(selector, options.slice(cursor + 1, close));
        cursor = close + 1;
    }
    const exact = branches.get(`=${count}`);
    const selected =
        exact ?? branches.get(pluralCategory(locale, count)) ?? branches.get("other") ?? "";
    return formatMessage(selected.replaceAll("#", String(count)), { ...params, count });
}

function formatToken(
    token: string,
    locale: Locale,
    params: Record<string, string | number>,
): string {
    let comma = -1;
    let depth = 0;
    for (let index = 0; index < token.length; index += 1) {
        const character = token[index];
        if (character === "{") depth += 1;
        else if (character === "}") depth -= 1;
        else if (character === "," && depth === 0) {
            comma = index;
            break;
        }
    }
    if (comma < 0) {
        const value = params[token.trim()];
        return value === undefined ? `{${token}}` : String(value);
    }
    const name = token.slice(0, comma).trim();
    const kind = token.slice(comma + 1).trimStart();
    if (kind.startsWith("plural,")) {
        const countValue = params[name];
        const count = typeof countValue === "number" ? countValue : Number(countValue ?? 0);
        return formatPlural(kind.slice("plural,".length), count, locale, params);
    }
    return `{${token}}`;
}

function formatMessage(
    template: string,
    params: Record<string, string | number>,
    locale: Locale = current,
): string {
    let output = "";
    let index = 0;
    while (index < template.length) {
        if (template[index] !== "{") {
            output += template[index];
            index += 1;
            continue;
        }
        const close = findClosing(template, index);
        if (close < 0) {
            output += template[index];
            index += 1;
            continue;
        }
        output += formatToken(template.slice(index + 1, close), locale, params);
        index = close + 1;
    }
    return output;
}

export function t(key: string, params: Record<string, string | number> = {}): string {
    const message = catalogs[current][key] ?? en[key] ?? key;
    return formatMessage(message, params);
}

export function tp(
    key: string,
    count: number,
    params: Record<string, string | number> = {},
): string {
    const message =
        catalogs[current][key] ?? en[key] ?? `{count, plural, one {# ${key}} other {# ${key}}}`;
    return formatMessage(message, { ...params, count });
}
