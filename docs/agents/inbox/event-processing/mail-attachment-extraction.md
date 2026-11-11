---
title: Design mail attachment extraction and access for recognition
kind: idea
area: mail-scanning
added: 2026-11-11
status: later
size: L
blocked-by: [mail-content-not-read]
---

Prerequisite for invoice/payment recognition stages that depend on attached invoices or remittance
advices ([initiative roadmap](README.md)). Attachments are currently out of scope of mail
scanning (SPEC-MAIL). Decide which attachment types are supported, how content is extracted or
referenced, whether raw attachments are stored durably for audit/replay, size/virus/safety limits,
and sensitive-data access/retention/redaction rules before implementing processors that rely on them.
