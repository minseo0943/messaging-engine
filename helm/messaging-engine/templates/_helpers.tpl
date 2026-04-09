{{/*
Chart name
*/}}
{{- define "messaging-engine.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fullname
*/}}
{{- define "messaging-engine.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "messaging-engine.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: messaging-engine
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/*
Service selector labels
*/}}
{{- define "messaging-engine.selectorLabels" -}}
app: {{ .serviceName }}
{{- end }}

{{/*
Service image
*/}}
{{- define "messaging-engine.image" -}}
{{ .global.imageRegistry }}/{{ .serviceName }}:{{ .global.imageTag }}
{{- end }}
