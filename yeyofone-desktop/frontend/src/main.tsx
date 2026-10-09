import { Accounts } from "./Accounts";
import { NativeStatus } from "./NativeStatus";
import { useLanguage, LanguageProvider, LanguageSelector } from "./i18n";
import { useState, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
import "./sidebar.css";
import { CallWindows, type CallWindowKind } from "./CallWindows";
import { Dialer } from "./Dialer";
import { LiveCall } from "./LiveCall";
import { IncomingCall } from "./IncomingCall";
import { CallHistory } from "./CallHistory";
import { Voicemail } from "./Voicemail";
import { ConferenceRoom } from "./ConferenceRoom";
import {
  CallingFeatures,
  initialRoutingPreview,
  type CallingFeature,
} from "./CallingFeatures";

type IconName =
  | "grid"
  | "chart"
  | "settings"
  | "users"
  | "phone"
  | "play"
  | "search"
  | "bell"
  | "download"
  | "arrow"
  | "conference";
function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const paths: Record<IconName, ReactNode> = {
    conference: (
      <>
        <rect x="2" y="3" width="16" height="12" rx="2" />
        <path d="m18 7 4-2v8l-4-2M5 20h10" />
        <circle cx="8" cy="8" r="1.5" />
        <path d="M5 12a3 3 0 0 1 6 0" />
      </>
    ),
    grid: (
      <>
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
      </>
    ),
    chart: (
      <>
        <path d="M4 20V5h4v15M10 20V2h4v18M16 20V9h4v11" />
      </>
    ),
    settings: (
      <>
        <path d="m9 3-1 3-3 1-2 4 2 2v4l3 1 2 3h4l1-3 3-1 2-4-2-2V7l-3-1-2-3Z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    users: (
      <>
        <circle cx="9" cy="7" r="3" />
        <path d="M3 21v-3a6 6 0 0 1 12 0v3ZM16 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 5v2" />
      </>
    ),
    phone: (
      <path d="m7 3 3 5-3 3c2 3 3 4 6 6l3-3 5 3c0 3-2 5-5 4C9 19 5 15 3 8 2 5 4 3 7 3Z" />
    ),
    play: <path d="m8 4 12 8-12 8Z" />,
    search: (
      <>
        <circle cx="10" cy="10" r="7" />
        <path d="m15 15 6 6" />
      </>
    ),
    bell: (
      <>
        <path d="M5 17h14l-2-4V9a5 5 0 0 0-10 0v4ZM10 21h4" />
      </>
    ),
    download: (
      <>
        <path d="M12 3v12m-5-5 5 5 5-5M4 15v6h16v-6" />
      </>
    ),
    arrow: <path d="M4 12h16m-5-5 5 5-5 5" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
const people = [
  { name: "Sarah Ndio", role: "Team lead", initials: "SN", color: "#e8dff4" },
  { name: "Alex Morgan", role: "Operator", initials: "AM", color: "#d9e9ed" },
  { name: "Maya Wilson", role: "Operator", initials: "MW", color: "#f7ddd2" },
  { name: "Daniel Evans", role: "Operator", initials: "DE", color: "#e3ebd6" },
];
const records = Array.from({ length: 10 }, (_, i) => ({
  id: i,
  person: people[i % 4]!,
  time: ["18:57", "18:41", "18:37", "18:25", "18:21"][i % 5]!,
  number: `+44 7700 900${String(i + 10).padStart(3, "0")}`,
  duration: ["05:12", "03:24", "02:17", "01:33", "04:25"][i % 5]!,
  incoming: i % 3 !== 0,
}));
const groups = [
  {
    name: "Sales enquiries",
    calls: 124,
    answered: 120,
    color: "#57b6ee",
    trend: [91, 87, 91, 91, 83, 92, 91],
  },
  {
    name: "Website enquiries",
    calls: 356,
    answered: 343,
    color: "#add979",
    trend: [62, 72, 81, 66, 64, 77, 86],
  },
  {
    name: "Customer support",
    calls: 207,
    answered: 168,
    color: "#f7bc3f",
    trend: [76, 57, 77, 81, 59, 73, 68],
  },
  {
    name: "Priority accounts",
    calls: 401,
    answered: 289,
    color: "#ee0864",
    trend: [37, 28, 40, 34, 29, 37, 55],
  },
];
function Avatar({
  person,
  online = false,
}: {
  person: (typeof people)[number];
  online?: boolean;
}) {
  return (
    <span className="avatar" style={{ background: person.color }}>
      {person.initials}
      {online && <i />}
    </span>
  );
}
function TrendChart({
  hourly = false,
  visible = groups.map((g) => g.name),
}: {
  hourly?: boolean;
  visible?: string[];
}) {
  const { t } = useLanguage();
  const labels = hourly
    ? ["08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00"]
    : ["04.07", "05.07", "06.07", "07.07", "08.07", "09.07", "10.07"];
  const lines = hourly
    ? [
        {
          name: "Call volume",
          color: "#ee0864",
          trend: [22, 42, 61, 80, 35, 20, 52],
        },
      ]
    : groups.filter((g) => visible.includes(g.name));
  const coords = (values: number[]) =>
    values.map((v, i) => `${45 + i * 74},${165 - v * 1.45}`).join(" ");
  return (
    <svg
      className="trend-chart"
      viewBox="0 0 520 200"
      role="img"
      aria-label={
        hourly
          ? "Sample call volume throughout the day"
          : "Sample weekly answer rates by team"
      }
    >
      <defs>
        <linearGradient
          id={hourly ? "hourFill" : "weekFill"}
          x1="0"
          y1="0"
          x2="0"
          y2="1"
        >
          <stop offset="0%" stopColor="#d5ddeb" stopOpacity=".65" />
          <stop offset="100%" stopColor="#d5ddeb" stopOpacity=".2" />
        </linearGradient>
      </defs>
      {[25, 70, 115, 160].map((y) => (
        <line key={y} x1="45" x2="489" y1={y} y2={y} stroke="#edf0f7" />
      ))}
      {lines.map((line) => (
        <g key={line.name}>
          <polygon
            points={`45,165 ${coords(line.trend)} 489,165`}
            fill={`url(#${hourly ? "hourFill" : "weekFill"})`}
          />
          <polyline
            points={coords(line.trend)}
            fill="none"
            stroke={line.color}
            strokeWidth="2"
            strokeLinejoin="round"
          />
        </g>
      ))}
      {labels.map((label, i) => (
        <text key={t(label)} x={45 + i * 74} y="187" textAnchor="middle">
          {t(label)}
        </text>
      ))}
      {!hourly && (
        <>
          <text x="0" y="27">
            100%
          </text>
          <text x="9" y="163">
            50%
          </text>
        </>
      )}
    </svg>
  );
}
function App() {
  const { t } = useLanguage();
  const [page, setPage] = useState("Overview");
  const [search, setSearch] = useState("");
  const [direction, setDirection] = useState("Incoming");
  const [period, setPeriod] = useState("This week");
  const [selected, setSelected] = useState(groups.map((g) => g.name));
  const [notice, setNotice] = useState("");
  const [conferenceOpen, setConferenceOpen] = useState(false);
  const [callingFeature, setCallingFeature] = useState<CallingFeature | null>(
    null,
  );
  const [routingPreview, setRoutingPreview] = useState(initialRoutingPreview);
  const [callWindow, setCallWindow] = useState<CallWindowKind | null>(null);
  const [contactIndex, setContactIndex] = useState(0);
  const contact = people[contactIndex]!;
  const filtered = records.filter(
    (r) =>
      (direction === "Incoming" ? r.incoming : !r.incoming) &&
      `${r.person.name} ${r.number}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#" onClick={() => setPage("Statistics")}>
          <span className="brand-dots" aria-hidden="true">
            {Array.from({ length: 9 }, (_, i) => (
              <i key={i} />
            ))}
          </span>
          YeyoFone
        </a>
        <label className="search">
          <Icon name="search" />
          <input
            aria-label={t("Search calls and operators")}
            placeholder={t("Search calls and operators")}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button
              aria-label={t("Clear search")}
              onClick={() => setSearch("")}
            >
              ×
            </button>
          )}
        </label>
        <LanguageSelector />
        <button
          className="notification icon-button"
          aria-label={t("Show notifications")}
          onClick={() =>
            setNotice(
              "You’re viewing sample data. Live notifications will appear when your account is connected.",
            )
          }
        >
          <Icon name="bell" size={28} />
          <i />
        </button>
        <div className="profile">
          <Avatar person={people[0]!} online />
          <div>
            <strong>Sarah Ndio</strong>
            <small>{t("Team lead")}</small>
          </div>
        </div>
      </header>
      <aside className="sidebar" aria-label={t("Main navigation")}>
        <nav>
          {(
            [
              ["Overview", "grid"],
              ["Call history", "phone"],
              ["Voicemail", "play"],
              ["Statistics", "chart"],
              ["Settings", "settings"],
              ["Contacts", "users"],
            ] as const
          ).map(([label, icon]) => (
            <button
              key={label}
              data-icon={icon}
              className={page === label ? "nav-item active" : "nav-item"}
              aria-label={t(label)}
              title={t(label)}
              aria-current={page === label ? "page" : undefined}
              onClick={() => {
                setPage(label);
                setNotice("");
              }}
            >
              <span className="sidebar-icon-disc">
                <Icon name={icon} size={25} />
              </span>
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <button
            className="nav-item"
            data-icon="phone"
            aria-label={t("Open phone dialer")}
            title={t("Make a call")}
            onClick={() => setCallWindow("dialer")}
          >
            <span className="sidebar-icon-disc">
              <Icon name="phone" size={24} />
            </span>
          </button>
          <button
            className="nav-item"
            data-icon="play"
            aria-label={t("Recordings availability")}
            title={t("Recordings")}
            onClick={() =>
              setNotice(
                "Sample recordings have no audio attached. Live recordings are not connected yet.",
              )
            }
          >
            <span className="sidebar-icon-disc">
              <Icon name="play" size={24} />
            </span>
          </button>
          <button
            className="nav-item"
            data-icon="conference"
            aria-label={t("Video conference")}
            title={t("Video conference")}
            onClick={() => setConferenceOpen(true)}
          >
            <span className="sidebar-icon-disc">
              <Icon name="conference" size={24} />
            </span>
          </button>
        </div>
      </aside>
      <main className="workspace">
        <div className="preview-note">
          <span>
            <i /> {t("Design preview")} <span className="note-divider">/</span>{" "}
            {t("Sample data")}
          </span>
          <NativeStatus />
        </div>
        <div className="call-preview-toolbar">
          <span>{t("Call screens")}</span>
          <button onClick={() => setCallWindow("incoming")}>
            <Icon name="phone" size={18} /> {t("Incoming call")}
          </button>
          <button onClick={() => setCallWindow("dialer")}>
            <Icon name="grid" size={18} /> {t("Make a call")}
          </button>
          <button onClick={() => setCallWindow("video")}>
            <Icon name="play" size={18} /> {t("Video call")}
          </button>
          <button onClick={() => setConferenceOpen(true)}>
            <Icon name="conference" size={20} /> {t("Video conference")}
          </button>
          <button onClick={() => setCallingFeature("forwarding")}>
            <Icon name="arrow" size={20} />
            {t("Call forwarding")}
          </button>
          <button onClick={() => setCallingFeature("ring-groups")}>
            <Icon name="users" size={20} />
            {t("Ring groups")}
          </button>
          <button onClick={() => setCallingFeature("audio-conference")}>
            <Icon name="phone" size={20} />
            {t("Audio conference")}
          </button>
        </div>
        {notice && (
          <div className="notice" role="status">
            {t(notice)}
            <button
              aria-label={t("Dismiss notification")}
              onClick={() => setNotice("")}
            >
              ×
            </button>
          </div>
        )}
        {page === "Overview" ? (
          <Overview
            search={search}
            onNotice={setNotice}
            onContact={(index) => {
              setContactIndex(index);
              setPage("Contacts");
            }}
          />
        ) : page === "Call history" ? (
          <CallHistory search={search} />
        ) : page === "Voicemail" ? (
          <Voicemail />
        ) : page === "Statistics" ? (
          <div className="dashboard-grid">
            <div className="left-column">
              <section>
                <div className="section-heading">
                  <h1>{t("Statistics")}</h1>
                  <div className="filters">
                    <select
                      aria-label={t("Statistics period")}
                      value={period}
                      onChange={(e) => setPeriod(e.target.value)}
                    >
                      <option value="This week">{t("This week")}</option>
                      <option value="Last week">{t("Last week")}</option>
                    </select>
                    <span>
                      {t(
                        period === "This week"
                          ? "04 July – 10 July"
                          : "27 June – 03 July",
                      )}
                    </span>
                  </div>
                </div>
                <div className="team-table">
                  <div className="team-row table-heading">
                    <span>{t("Team")}</span>
                    <span>{t("Calls")}</span>
                    <span>{t("Answered")}</span>
                    <span>{t("Answer rate")}</span>
                    <span />
                  </div>
                  {groups.map((group, i) => (
                    <button
                      className={`team-row ${selected.includes(group.name) ? "" : "muted"}`}
                      key={t(group.name)}
                      aria-pressed={selected.includes(group.name)}
                      onClick={() =>
                        setSelected((old) =>
                          old.includes(group.name)
                            ? old.filter((n) => n !== group.name)
                            : [...old, group.name],
                        )
                      }
                      title={t("Toggle team on chart")}
                    >
                      <span className="team-name">
                        <span className="row-number">{i + 1}</span>
                        <i style={{ background: group.color }} />
                        {t(group.name)}
                      </span>
                      <span>
                        {period === "This week"
                          ? group.calls
                          : Math.round(group.calls * 0.87)}
                      </span>
                      <span>
                        {period === "This week"
                          ? group.answered
                          : Math.round(group.answered * 0.87)}
                      </span>
                      <span>
                        {((group.answered / group.calls) * 100).toFixed(1)}%
                      </span>
                      <span className="ellipsis">···</span>
                    </button>
                  ))}
                </div>
                <div className="chart-card weekly">
                  <TrendChart visible={selected} />
                  {selected.length === 0 && (
                    <p className="chart-empty">
                      {t("Select a team above to show its trend.")}
                    </p>
                  )}
                </div>
              </section>
              <section className="call-statistics">
                <div className="section-heading">
                  <h2>{t("Call statistics")}</h2>
                  <span className="filter-label">
                    {t("Priority accounts")} <span>⌄</span>
                  </span>
                </div>
                <div className="chart-card">
                  <TrendChart hourly />
                </div>
                <div className="metric-grid">
                  <div className="metric-card">
                    <div>
                      <small>
                        {t("Incoming")} <i className="metric-dot pale" />
                      </small>
                      <strong>154</strong>
                      <small>
                        {t("Answered")} <i className="metric-dot pink" />
                      </small>
                      <strong>92</strong>
                    </div>
                    <div className="donut">
                      <svg viewBox="0 0 120 120" aria-hidden="true">
                        <circle
                          cx="60"
                          cy="60"
                          r="51"
                          fill="none"
                          stroke="#edf0f8"
                          strokeWidth="3"
                        />
                        <circle
                          cx="60"
                          cy="60"
                          r="51"
                          fill="none"
                          stroke="#ee0864"
                          strokeWidth="3.5"
                          strokeDasharray="191 321"
                          strokeLinecap="round"
                          transform="rotate(-90 60 60)"
                        />
                      </svg>
                      <span>
                        59.7<small>%</small>
                      </span>
                    </div>
                  </div>
                  <div
                    className="bar-card"
                    aria-label="Sample daily call volume"
                  >
                    <div className="bars">
                      {[42, 65, 50, 38, 29, 76, 63, 34, 45, 58].map(
                        (height, i) => (
                          <div key={i}>
                            <span style={{ height: `${height + 17}%` }} />
                            <i style={{ height: `${height}%` }} />
                          </div>
                        ),
                      )}
                    </div>
                    <span className="bar-caption">
                      {t("01 July – 10 July")}
                    </span>
                  </div>
                </div>
              </section>
            </div>
            <div className="right-column">
              <section>
                <div className="section-heading recordings-heading">
                  <h2>{t("Call recordings")}</h2>
                  <div className="tabs" aria-label={t("Call direction")}>
                    {["Incoming", "Outgoing"].map((tab) => (
                      <button
                        key={t(tab)}
                        aria-pressed={direction === tab}
                        className={direction === tab ? "selected" : ""}
                        onClick={() => setDirection(tab)}
                      >
                        {t(tab)}
                      </button>
                    ))}
                  </div>
                  <span className="filter-label">{t("10 July")}</span>
                </div>
                <div className="recording-table">
                  <div className="record-row table-heading">
                    <span>{t("Operator")}</span>
                    <span>{t("Time")}</span>
                    <span>{t("Customer number")}</span>
                    <span>{t("Duration")}</span>
                    <span />
                  </div>
                  {filtered.map((record) => (
                    <div className="record-row" key={record.id}>
                      <div className="operator-name">
                        <Avatar person={record.person} />
                        <div>
                          <strong>{record.person.name}</strong>
                          <small>{t(record.person.role)}</small>
                        </div>
                      </div>
                      <span>{record.time}</span>
                      <span>{record.number}</span>
                      <span>00:{record.duration}</span>
                      <div className="record-actions">
                        <button
                          className="icon-button"
                          aria-label={`Preview recording from ${record.person.name}`}
                          onClick={() =>
                            setNotice(
                              "This is a design preview. No audio is attached to sample recordings.",
                            )
                          }
                        >
                          <Icon name="play" size={18} />
                        </button>
                        <button
                          className="icon-button"
                          aria-label={`Download recording from ${record.person.name}`}
                          onClick={() =>
                            setNotice(
                              "There is no downloadable audio for this sample recording.",
                            )
                          }
                        >
                          <Icon name="download" size={18} />
                        </button>
                      </div>
                    </div>
                  ))}
                  {filtered.length === 0 && (
                    <p className="empty-state">
                      {t("No sample calls match your search.")}
                    </p>
                  )}
                </div>
                <p className="record-count">
                  {t(`${filtered.length} sample recordings`)}
                </p>
              </section>
              <section className="operators-section">
                <div className="section-heading">
                  <h2>{t("Operator status")}</h2>
                  <span className="subtle">{t("3 available · 1 offline")}</span>
                </div>
                <OperatorCards search={search} />
              </section>
            </div>
          </div>
        ) : page === "Contacts" ? (
          <div className="contact-page">
            <div className="contact-breadcrumb">
              <span>{t("Contacts")}</span>
              <span>›</span>
              <select
                aria-label={t("Select contact")}
                value={contactIndex}
                onChange={(e) => setContactIndex(Number(e.target.value))}
              >
                {people.map((person, i) => (
                  <option key={person.name} value={i}>
                    {person.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="contact-grid">
              <div className="contact-left">
                <section className="contact-profile">
                  <div className="contact-portrait">
                    <Avatar person={contact} />
                  </div>
                  <div className="contact-information">
                    <h1>{contact.name}</h1>
                    <p>
                      {t(
                        contact.role === "Team lead"
                          ? "Senior Manager"
                          : contact.role,
                      )}
                    </p>
                    <div className="contact-line">
                      <Icon name="phone" />
                      <span>
                        +44 7700 900010{" "}
                        <span className="extension">
                          ext. {1005 + contactIndex}
                        </span>
                      </span>
                    </div>
                    <div className="contact-line">
                      <span className="contact-mobile" aria-hidden="true">
                        ▯
                      </span>
                      <span>+44 7700 9000{20 + contactIndex}</span>
                    </div>
                    <div className="contact-line contact-email">
                      <span aria-hidden="true">✉</span>
                      <span>
                        {contact.name.toLowerCase().replace(" ", ".")}
                        @example.com
                      </span>
                    </div>
                  </div>
                </section>
                <section className="participation-section">
                  <h2>{t("Group participation")}</h2>
                  <div className="participation-row table-heading">
                    <span>{t("Title")}</span>
                    <span>{t("Members")}</span>
                    <span>{t("Position in a group")}</span>
                  </div>
                  {[
                    ["West Callcenter", "24", "Head of group"],
                    ["Team leaders", "12", "Member"],
                    ["Sales Department", "35", "Head of group"],
                  ].map((row) => (
                    <div className="participation-row" key={row[0]}>
                      {row.map((value, i) => (
                        <span key={i}>{t(value)}</span>
                      ))}
                    </div>
                  ))}
                </section>
                <section className="participation-section">
                  <h2>{t("Route participation")}</h2>
                  <div className="participation-row table-heading">
                    <span>{t("Route")}</span>
                    <span>{t("Element")}</span>
                    <span>{t("Queue")}</span>
                  </div>
                  {[
                    ["Sales enquiries", "Transfer to user", "—"],
                    ["Sales enquiries", "Queue", "West Callcenter"],
                    ["Website enquiries", "Queue", "West Callcenter"],
                  ].map((row, index) => (
                    <div className="participation-row" key={index}>
                      {row.map((value, i) => (
                        <span key={i}>{t(value)}</span>
                      ))}
                    </div>
                  ))}
                </section>
              </div>
              <section className="contact-recordings">
                <div className="section-heading recordings-heading">
                  <h2>{t("Call recordings")}</h2>
                  <div
                    className="tabs"
                    aria-label={t("Contact call direction")}
                  >
                    {["Incoming", "Outgoing"].map((tab) => (
                      <button
                        key={t(tab)}
                        aria-pressed={direction === tab}
                        className={direction === tab ? "selected" : ""}
                        onClick={() => setDirection(tab)}
                      >
                        {t(tab)}
                      </button>
                    ))}
                  </div>
                  <span className="filter-label">
                    {t("10 July")} <span>⌄</span>
                  </span>
                </div>
                <div className="contact-record-table">
                  <div className="contact-record-row table-heading">
                    <span>{t("Time")}</span>
                    <span>{t("Client number")}</span>
                    <span>{t("Duration")}</span>
                    <span />
                  </div>
                  {records
                    .filter(
                      (r) =>
                        (direction === "Incoming" ? r.incoming : !r.incoming) &&
                        (!search ||
                          `${contact.name} ${r.number}`
                            .toLowerCase()
                            .includes(search.toLowerCase())),
                    )
                    .map((record) => (
                      <div className="contact-record-row" key={record.id}>
                        <span>{record.time}</span>
                        <span>{record.number}</span>
                        <span>00:{record.duration}</span>
                        <div className="record-actions">
                          <button
                            className="icon-button"
                            aria-label={`Play sample call at ${record.time}`}
                            onClick={() =>
                              setNotice(
                                "This is a design preview. No audio is attached to sample recordings.",
                              )
                            }
                          >
                            <Icon name="play" size={19} />
                          </button>
                          <button
                            className="icon-button"
                            aria-label={`Download sample call at ${record.time}`}
                            onClick={() =>
                              setNotice(
                                "There is no downloadable audio for this sample recording.",
                              )
                            }
                          >
                            <Icon name="download" size={20} />
                          </button>
                        </div>
                      </div>
                    ))}
                </div>
                <p className="record-count">
                  {t(`Sample recordings · ${contact.name}`)}
                </p>
              </section>
            </div>
          </div>
        ) : page === "Operators" ? (
          <section className="standalone">
            <div className="section-heading">
              <h1>{t("Operators")}</h1>
              <span className="subtle">{t("Sample team")}</span>
            </div>
            <OperatorCards search={search} />
          </section>
        ) : (
          <section className="standalone settings-panel">
            <h1>{t("Settings")}</h1>
            <Accounts />
            <h2>{t("Appearance")}</h2>
            <p>{t("Light theme · Compact sidebar · Desktop dashboard")}</p>
          </section>
        )}
        <footer>
          YeyoFone Desktop{" "}
          <span>{t("Sample workspace · No live calls connected")}</span>
        </footer>
      </main>
      {callingFeature && (
        <CallingFeatures
          key={callingFeature}
          kind={callingFeature}
          settings={routingPreview}
          onSave={setRoutingPreview}
          onClose={() => setCallingFeature(null)}
        />
      )}
      {conferenceOpen && (
        <ConferenceRoom onClose={() => setConferenceOpen(false)} />
      )}
      <LiveCall onOpen={() => setCallWindow("dialer")} />
      <IncomingCall />
      {callWindow &&
        (callWindow === "dialer" ? (
          <Dialer onClose={() => setCallWindow(null)} />
        ) : (
          <CallWindows
            key={callWindow}
            kind={callWindow}
            onClose={() => setCallWindow(null)}
          />
        ))}
    </div>
  );
}
function Overview({
  search,
  onNotice,
  onContact,
}: {
  search: string;
  onNotice: (message: string) => void;
  onContact: (index: number) => void;
}) {
  const { t } = useLanguage();
  const [direction, setDirection] = useState("Incoming");
  const [routeDirection, setRouteDirection] = useState("Incoming");
  const [route, setRoute] = useState("Site sale");
  const [paused, setPaused] = useState<string[]>([]);
  const routes = [
    { name: "Site sale", total: 124, answered: "71.7%" },
    { name: "Newspaper sale", total: 76, answered: "64.5%" },
    { name: "Internet sale", total: 212, answered: "80.1%" },
  ];
  const queues = [
    {
      name: "East call center",
      operators: 3,
      waiting: 12,
      route: "Site sale",
      color: "#ee0864",
    },
    {
      name: "West call center",
      operators: 2,
      waiting: 4,
      route: "Newspaper sale",
      color: "#f4c441",
    },
    {
      name: "South call center",
      operators: 2,
      waiting: 4,
      route: "Internet sale",
      color: "#b6db83",
    },
  ];
  const calls = records.filter(
    (r) =>
      (direction === "Incoming" ? r.incoming : !r.incoming) &&
      `${r.person.name} ${r.number}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    <div className="overview-grid">
      <div className="overview-left">
        <section>
          <div className="section-heading">
            <h1>{t("Call statistics")}</h1>
            <div className="filters">
              <select
                aria-label={t("Statistics route")}
                value={route}
                onChange={(e) => setRoute(e.target.value)}
              >
                {routes.map((r) => (
                  <option key={t(r.name)} value={t(r.name)}>
                    {t(r.name)}
                  </option>
                ))}
              </select>
              <span>{t("10 July ⌄")}</span>
            </div>
          </div>
          <div className="chart-card overview-chart">
            <TrendChart hourly />
          </div>
          <div className="metric-grid">
            <div className="metric-card">
              <div>
                <small>
                  {t("Total")} <i className="metric-dot pale" />
                </small>
                <strong>
                  {route === "Site sale"
                    ? 154
                    : route === "Newspaper sale"
                      ? 98
                      : 246}
                </strong>
                <small>
                  {t("Answered")} <i className="metric-dot blue" />
                </small>
                <strong>
                  {route === "Site sale"
                    ? 92
                    : route === "Newspaper sale"
                      ? 59
                      : 147}
                </strong>
              </div>
              <div className="donut">
                <svg viewBox="0 0 120 120" aria-hidden="true">
                  <circle
                    cx="60"
                    cy="60"
                    r="51"
                    fill="none"
                    stroke="#edf0f8"
                    strokeWidth="3"
                  />
                  <circle
                    cx="60"
                    cy="60"
                    r="51"
                    fill="none"
                    stroke="#58b2ed"
                    strokeWidth="3.5"
                    strokeDasharray="191 321"
                    strokeLinecap="round"
                    transform="rotate(-90 60 60)"
                  />
                </svg>
                <span>
                  59.7<small>%</small>
                </span>
              </div>
            </div>
            <div className="bar-card">
              <div className="bars">
                {[42, 65, 50, 38, 29, 76, 63, 34, 45, 58].map((height, i) => (
                  <div key={i}>
                    <span style={{ height: `${height + 17}%` }} />
                    <i style={{ height: `${height}%` }} />
                  </div>
                ))}
              </div>
              <span className="bar-caption">{t("01 July – 10 July")}</span>
            </div>
          </div>
        </section>
        <section className="overview-recordings">
          <div className="section-heading recordings-heading">
            <h2>{t("Call recordings")}</h2>
            <div className="tabs" aria-label={t("Overview call direction")}>
              {["Incoming", "Outgoing"].map((tab) => (
                <button
                  key={t(tab)}
                  aria-pressed={direction === tab}
                  className={direction === tab ? "selected" : ""}
                  onClick={() => setDirection(tab)}
                >
                  {t(tab)}
                </button>
              ))}
            </div>
            <span className="filter-label">{t("10 July ⌄")}</span>
          </div>
          <div className="overview-record-row table-heading">
            <span>{t("Time")}</span>
            <span>{t("Operator")}</span>
            <span>{t("Client number")}</span>
            <span>{t("Duration")}</span>
            <span />
          </div>
          {calls.map((record) => (
            <div className="overview-record-row" key={record.id}>
              <span>{record.time}</span>
              <div className="operator-name">
                <Avatar person={record.person} />
                <div>
                  <strong>{record.person.name}</strong>
                  <small>{t(record.person.role)}</small>
                </div>
              </div>
              <span>{record.number}</span>
              <span>00:{record.duration}</span>
              <div className="record-actions">
                <button
                  className="icon-button"
                  aria-label={`Play sample recording at ${record.time}`}
                  onClick={() =>
                    onNotice("No audio is attached to this sample recording.")
                  }
                >
                  <Icon name="play" size={18} />
                </button>
                <button
                  className="icon-button"
                  aria-label={`Download sample recording at ${record.time}`}
                  onClick={() =>
                    onNotice(
                      "Sample recordings do not contain downloadable audio.",
                    )
                  }
                >
                  <Icon name="download" size={18} />
                </button>
              </div>
            </div>
          ))}
          {calls.length === 0 && (
            <p className="empty-state">
              {t("No sample recordings match your search.")}
            </p>
          )}
        </section>
      </div>
      <div className="overview-right">
        <section>
          <div className="section-heading">
            <h2>{t("Routes")}</h2>
            <div className="tabs" aria-label={t("Route direction")}>
              {["Incoming", "Outgoing"].map((tab) => (
                <button
                  key={t(tab)}
                  className={routeDirection === tab ? "selected" : ""}
                  aria-pressed={routeDirection === tab}
                  onClick={() => setRouteDirection(tab)}
                >
                  {t(tab)}
                </button>
              ))}
            </div>
          </div>
          <div className="route-row table-heading">
            <span>{t("Name")}</span>
            <span>{t("Total")}</span>
            <span>{t("Answered")}</span>
            <span />
            <span />
          </div>
          {routes
            .filter((r) => r.name.toLowerCase().includes(search.toLowerCase()))
            .map((r) => (
              <div className="route-row" key={t(r.name)}>
                <strong>{t(r.name)}</strong>
                <span>
                  {routeDirection === "Incoming"
                    ? r.total
                    : Math.round(r.total * 0.6)}
                </span>
                <span>{r.answered}</span>
                <button
                  className={`pause-button ${paused.includes(r.name) ? "paused" : ""}`}
                  aria-pressed={paused.includes(r.name)}
                  onClick={() => {
                    setPaused((old) =>
                      old.includes(r.name)
                        ? old.filter((n) => n !== r.name)
                        : [...old, r.name],
                    );
                    onNotice(
                      "Route status changed in this design preview only. Live routing is not connected.",
                    );
                  }}
                >
                  {t(paused.includes(r.name) ? "▷ Resume" : "Ⅱ Pause")}
                </button>
                <button
                  className="ellipsis"
                  aria-label={`Details for ${t(r.name)}`}
                  onClick={() =>
                    onNotice(
                      `${t(r.name)}: sample route. No live routing configuration is connected.`,
                    )
                  }
                >
                  ···
                </button>
              </div>
            ))}
        </section>
        <section className="overview-queues">
          <div className="section-heading">
            <h2>{t("Queues")}</h2>
          </div>
          <div className="queue-row table-heading">
            <span>{t("Name")}</span>
            <span>{t("Operators")}</span>
            <span>{t("In queue")}</span>
            <span>{t("Route")}</span>
            <span />
          </div>
          {queues
            .filter((q) =>
              `${t(q.name)} ${t(q.route)}`
                .toLowerCase()
                .includes(search.toLowerCase()),
            )
            .map((q) => (
              <div className="queue-row" key={t(q.name)}>
                <strong>{t(q.name)}</strong>
                <span>{q.operators}</span>
                <span className="queue-count">
                  <i style={{ borderColor: q.color }} />
                  {q.waiting}
                </span>
                <strong>{t(q.route)}</strong>
                <button
                  className="ellipsis"
                  aria-label={`Details for ${t(q.name)}`}
                  onClick={() =>
                    onNotice(
                      `${t(q.name)}: ${q.operators} sample operators, ${q.waiting} sample waiting calls. No live queue is connected.`,
                    )
                  }
                >
                  ···
                </button>
              </div>
            ))}
        </section>
        <section className="overview-contacts">
          <div className="section-heading">
            <h2>{t("Contacts")}</h2>
          </div>
          <div className="overview-contact-grid">
            {people
              .filter((p) =>
                p.name.toLowerCase().includes(search.toLowerCase()),
              )
              .map((person) => {
                const i = people.indexOf(person);
                return (
                  <button
                    className="overview-contact-card"
                    key={person.name}
                    onClick={() => onContact(i)}
                    aria-label={t(`Open contact ${person.name}`)}
                  >
                    <div className="operator-name">
                      <Avatar person={person} online />
                      <div>
                        <strong>{person.name}</strong>
                        <small>{t(person.role)}</small>
                      </div>
                    </div>
                    <div className="contact-card-number">
                      <span>{1005 + i}</span>
                      <span>+44 7700 9000{10 + i}</span>
                    </div>
                    <span className="contact-card-email">
                      {person.name.toLowerCase().replace(" ", ".")}@example.com
                    </span>
                  </button>
                );
              })}
          </div>
        </section>
      </div>
    </div>
  );
}

function OperatorCards({ search }: { search: string }) {
  const { t } = useLanguage();
  const visible = people.filter((p) =>
    p.name.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <div className="operator-grid">
      {visible.map((person) => {
        const i = people.indexOf(person);
        return (
          <article className="operator-card" key={person.name}>
            <div className="operator-name">
              <Avatar person={person} online={i !== 3} />
              <div>
                <strong>{person.name}</strong>
                <small>{t(person.role)}</small>
              </div>
            </div>
            {i !== 3 ? (
              <>
                <span className="operator-time">
                  {["03:32", "01:26", "02:14"][i]}
                </span>
                <div className="operator-detail">
                  <Icon name="arrow" size={14} />
                  <span>+44 7700 9000{i + 10}</span>
                </div>
              </>
            ) : (
              <p className="offline">{t("Unavailable")}</p>
            )}
          </article>
        );
      })}
      {visible.length === 0 && (
        <p className="empty-state">{t("No operators match your search.")}</p>
      )}
    </div>
  );
}
const root = document.getElementById("root");
if (!root) throw new Error("Missing application root");
createRoot(root).render(
  <LanguageProvider>
    <App />
  </LanguageProvider>,
);
