# Política de privacidad — PdfKit Pro

**Última actualización:** 4 de agosto de 2026

PdfKit Pro («la aplicación», «nosotros») es una aplicación para Android desarrollada por [hhuezo](https://github.com/hhuezo). Esta política describe qué información se utiliza, cómo se trata y qué derechos tienes como usuario.

Si tienes preguntas sobre esta política, puedes contactarnos abriendo un issue en GitHub:  
https://github.com/hhuezo/totalPdf/issues

---

## 1. Resumen

PdfKit Pro te permite leer, convertir, firmar, unir, escanear y editar archivos PDF. **El procesamiento de tus documentos se realiza en tu dispositivo.** No vendemos tus datos, no mostramos publicidad y no recopilamos información con fines de marketing.

---

## 2. Información que accede la aplicación

La aplicación puede acceder a los siguientes datos **solo cuando tú eliges usar una función**:

| Tipo de dato | Para qué se usa |
|---|---|
| Archivos PDF | Abrir, leer, convertir, firmar, unir o eliminar páginas |
| Imágenes de la galería | Crear PDFs o agregar páginas escaneadas |
| Cámara | Escanear documentos y convertirlos a PDF |
| Metadatos de archivos recientes | Nombre, tamaño, URI, fecha de última apertura y última página leída |

La aplicación **no** solicita permiso de Internet y **no** envía tus PDFs ni imágenes a servidores propios.

---

## 3. Cómo se almacenan los datos

### 3.1 Archivos que creas o guardas

Cuando guardas un resultado, la aplicación puede almacenar archivos en tu dispositivo en:

- `Descargas/PdfKit Pro` (PDFs)
- `Imágenes/PdfKit Pro` (imágenes convertidas desde PDF)

Estos archivos permanecen en tu dispositivo bajo tu control.

### 3.2 Archivos recientes

Para mostrarte los PDFs abiertos recientemente, la aplicación guarda localmente (mediante Android DataStore) una lista con:

- URI del archivo
- Nombre visible
- Tamaño
- Fecha de última apertura
- Última página visitada

Esta información **no sale de tu dispositivo** y puedes eliminarla borrando los datos de la aplicación desde Ajustes de Android.

### 3.3 Copias de seguridad del sistema

La aplicación permite las copias de seguridad automáticas de Android (`allowBackup`). Dependiendo de la configuración de tu dispositivo, Google o el fabricante podrían incluir datos de la app en copias de seguridad del sistema. Consulta la configuración de backup de tu teléfono para más detalles.

---

## 4. Servicios de terceros

### 4.1 Google ML Kit Document Scanner

La función de escaneo utiliza **Google Play services — ML Kit Document Scanner** para detectar bordes y recortar documentos. Google puede procesar las imágenes según sus propias políticas cuando usas el escáner integrado.

- Política de privacidad de Google: https://policies.google.com/privacy

### 4.2 Bibliotecas de código abierto

La aplicación utiliza bibliotecas como PDFBox Android y componentes de Android Jetpack para procesar archivos localmente. Estas bibliotecas se ejecutan en tu dispositivo y no reciben tus documentos en servidores externos operados por nosotros.

---

## 5. Lo que no hacemos

- No creamos cuentas de usuario
- No recopilamos nombre, correo electrónico ni identificadores publicitarios
- No usamos analytics ni SDKs de seguimiento
- No mostramos anuncios
- No vendemos ni compartimos tus documentos con terceros con fines comerciales

---

## 6. Permisos de Android

| Permiso | Motivo |
|---|---|
| Cámara | Escanear documentos a PDF (opcional; solo cuando usas «Escanear») |
| Almacenamiento externo (Android 9 y anteriores) | Guardar PDFs en Descargas en dispositivos antiguos |

En versiones recientes de Android, el acceso a archivos se gestiona mediante el selector de archivos del sistema (Storage Access Framework), sin acceso amplio a todo el almacenamiento.

---

## 7. Menores de edad

PdfKit Pro no está dirigida específicamente a menores de 13 años. No recopilamos intencionalmente datos personales de niños. Si crees que un menor nos ha proporcionado información personal, contáctanos para revisarlo.

---

## 8. Seguridad

Procesamos tus archivos localmente en el dispositivo. Aun así, ningún método de transmisión o almacenamiento es 100 % seguro. Te recomendamos mantener tu dispositivo actualizado y protegido con bloqueo de pantalla.

---

## 9. Tus derechos y control

Puedes en cualquier momento:

- Dejar de usar funciones que requieran cámara o acceso a archivos
- Eliminar PDFs e imágenes guardados desde tu gestor de archivos
- Borrar los datos de la aplicación en **Ajustes → Aplicaciones → PdfKit Pro → Almacenamiento → Borrar datos**
- Desinstalar la aplicación, lo que elimina los datos locales que almacena (excepto archivos que hayas guardado manualmente en Descargas o Imágenes)

---

## 10. Cambios en esta política

Podemos actualizar esta política para reflejar cambios en la aplicación o requisitos legales. Publicaremos la versión actualizada en este mismo repositorio e indicaremos la fecha de «Última actualización» al inicio del documento.

---

## 11. Contacto

**Desarrollador:** hhuezo  
**Repositorio:** https://github.com/hhuezo/totalPdf  
**Contacto:** https://github.com/hhuezo/totalPdf/issues

---

*Esta política aplica a la aplicación PdfKit Pro (`com.hhuezo.pdfconverter`) publicada en Google Play.*
