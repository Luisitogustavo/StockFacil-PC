from pathlib import Path
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
QA_DIR = ROOT / "qa_metricas"
QA_DIR.mkdir(exist_ok=True)
OUT = ROOT / "Informe_StockFacil_PC_Metricas_hasta_5_7.docx"
CHART = QA_DIR / "mipyme_comercio_servicios_2024.png"

URL_INEI = "https://www.inei.gob.pe/media/MenuRecursivo/boletines/boletin-tic-oct_dic2025.pdf"
URL_PRODUCE_BOOK = "https://ogeiee.produce.gob.pe/index.php/en/2017-11-05-06-56-11/estadisticas-mipyme-2/item/download/2736_eeee9391e9ed619299baa926b1d97a08"
URL_PRODUCE_PAGE = "https://www.gob.pe/institucion/produce/noticias/1299194-numero-de-mipyme-en-peru-ascendio-a-2-33-millones-al-cierre-del-2024-alcanzando-un-crecimiento-de-1-6"
URL_PCM = "https://www.gob.pe/institucion/pcm/normas-legales/4471543-085-2023-pcm"
URL_ANPD = "https://www.gob.pe/institucion/anpd/normas-legales/6554453-n-016-2024-jus"
URL_ANDROID = "https://developer.android.com/training/data-storage/sqlite"
URL_IBM = "https://www.ibm.com/thought-leadership/institute-business-value/report/business-trends-2026"

NAVY = "183657"
BLUE = "28669B"
TEAL = "2D8B89"
INK = "202A35"
MUTED = "5F6B76"
PALE = "EAF1F7"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()


def make_chart():
    width, height = 1500, 590
    im = Image.new("RGB", (width, height), "white")
    d = ImageDraw.Draw(im)
    regular = font(r"C:\Windows\Fonts\arial.ttf", 30)
    bold = font(r"C:\Windows\Fonts\arialbd.ttf", 39)
    value_font = font(r"C:\Windows\Fonts\arialbd.ttf", 34)
    d.text((78, 34), "Mipyme formales por sector principal en 2024", font=bold, fill="#183657")
    d.text((80, 91), "Participación dentro del total de Mipyme formales", font=regular, fill="#5F6B76")
    left, right = 460, 1370
    top, bottom = 175, 500
    for pct in (0, 10, 20, 30, 40, 50):
        x = left + (right - left) * pct / 50
        d.line((x, top - 8, x, bottom), fill="#D9E1E8", width=2)
        label = f"{pct}%"
        box = d.textbbox((0, 0), label, font=regular)
        d.text((x - (box[2] - box[0]) / 2, bottom + 12), label, font=regular, fill="#536170")
    rows = [("Comercio", 44.2, BLUE), ("Servicios", 41.5, TEAL)]
    y_positions = [220, 370]
    for (label, value, color), y in zip(rows, y_positions):
        d.text((80, y + 5), label, font=bold, fill="#202A35")
        end = left + (right - left) * value / 50
        d.rounded_rectangle((left, y, end, y + 72), radius=12, fill=f"#{color}")
        d.text((min(end + 18, 1300), y + 15), f"{value:.1f}%", font=value_font, fill="#183657")
    im.save(CHART, quality=95)


def set_cell_margins(cell, top=100, start=120, bottom=100, end=120):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcMar = tcPr.first_child_found_in("w:tcMar")
    if tcMar is None:
        tcMar = OxmlElement("w:tcMar")
        tcPr.append(tcMar)
    for m, v in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tcMar.find(qn(f"w:{m}"))
        if node is None:
            node = OxmlElement(f"w:{m}")
            tcMar.append(node)
        node.set(qn("w:w"), str(v))
        node.set(qn("w:type"), "dxa")


def add_hyperlink(paragraph, label, url):
    part = paragraph.part
    rid = part.relate_to(url, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink", is_external=True)
    hyperlink = OxmlElement("w:hyperlink")
    hyperlink.set(qn("r:id"), rid)
    run = OxmlElement("w:r")
    rPr = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), BLUE)
    rPr.append(color)
    underline = OxmlElement("w:u")
    underline.set(qn("w:val"), "single")
    rPr.append(underline)
    run.append(rPr)
    text = OxmlElement("w:t")
    text.text = label
    run.append(text)
    hyperlink.append(run)
    paragraph._p.append(hyperlink)
    return hyperlink


def set_repeat_table_header(row):
    trPr = row._tr.get_or_add_trPr()
    tblHeader = OxmlElement("w:tblHeader")
    tblHeader.set(qn("w:val"), "true")
    trPr.append(tblHeader)


def keep_with_next(paragraph):
    paragraph.paragraph_format.keep_with_next = True


def add_body(doc, text, lead=None):
    p = doc.add_paragraph(style="Normal")
    p.paragraph_format.keep_together = True
    if lead and text.startswith(lead):
        r = p.add_run(lead)
        r.bold = True
        p.add_run(text[len(lead):])
    else:
        p.add_run(text)
    return p


def add_source_line(doc, label, links):
    p = doc.add_paragraph(style="Source Line")
    p.paragraph_format.keep_together = True
    p.add_run(label)
    for i, (name, url) in enumerate(links):
        if i:
            p.add_run("  ·  ")
        add_hyperlink(p, name, url)
    return p


def add_bullet(doc, text, bold_lead=None):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.keep_together = True
    if bold_lead and text.startswith(bold_lead):
        p.add_run(bold_lead).bold = True
        p.add_run(text[len(bold_lead):])
    else:
        p.add_run(text)
    return p


def add_objective(doc, heading, text, check):
    h = doc.add_paragraph(style="Heading 2")
    h.add_run(heading)
    keep_with_next(h)
    add_body(doc, text)
    p = doc.add_paragraph(style="Normal")
    p.paragraph_format.left_indent = Inches(0.18)
    p.paragraph_format.space_after = Pt(9)
    r = p.add_run("Comprobación: ")
    r.bold = True
    p.add_run(check)


def add_section_heading(doc, text):
    p = doc.add_paragraph(style="Heading 1")
    p.add_run(text)
    keep_with_next(p)
    return p


def set_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    paragraph.add_run("Página ").font.size = Pt(9)
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), "PAGE")
    paragraph._p.append(fld)


def style_document(doc):
    sec = doc.sections[0]
    sec.page_width = Inches(8.5)
    sec.page_height = Inches(11)
    sec.top_margin = Inches(0.72)
    sec.bottom_margin = Inches(0.7)
    sec.left_margin = Inches(0.82)
    sec.right_margin = Inches(0.82)
    sec.header_distance = Inches(0.32)
    sec.footer_distance = Inches(0.32)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Aptos"
    normal.font.size = Pt(11)
    normal.font.color.rgb = RGBColor.from_string(INK)
    normal.paragraph_format.space_after = Pt(7)
    normal.paragraph_format.line_spacing = 1.12

    title = styles["Title"]
    title.font.name = "Aptos Display"
    title.font.size = Pt(26)
    title.font.bold = True
    title.font.color.rgb = RGBColor.from_string("000000")
    title.paragraph_format.space_after = Pt(2)
    title_ppr = title._element.get_or_add_pPr()
    title_border = title_ppr.find(qn("w:pBdr"))
    if title_border is not None:
        title_ppr.remove(title_border)

    h1 = styles["Heading 1"]
    h1.font.name = "Aptos Display"
    h1.font.size = Pt(15)
    h1.font.bold = True
    h1.font.color.rgb = RGBColor.from_string(NAVY)
    h1.paragraph_format.space_before = Pt(15)
    h1.paragraph_format.space_after = Pt(7)
    h1.paragraph_format.keep_with_next = True

    h2 = styles["Heading 2"]
    h2.font.name = "Aptos"
    h2.font.size = Pt(12)
    h2.font.bold = True
    h2.font.color.rgb = RGBColor.from_string(BLUE)
    h2.paragraph_format.space_before = Pt(9)
    h2.paragraph_format.space_after = Pt(3)
    h2.paragraph_format.keep_with_next = True

    if "Source Line" not in [s.name for s in styles]:
        src = styles.add_style("Source Line", 1)
    else:
        src = styles["Source Line"]
    src.font.name = "Aptos"
    src.font.size = Pt(9)
    src.font.color.rgb = RGBColor.from_string(MUTED)
    src.paragraph_format.space_before = Pt(0)
    src.paragraph_format.space_after = Pt(8)
    src.paragraph_format.line_spacing = 1.0

    bullet = styles["List Bullet"]
    bullet.font.name = "Aptos"
    bullet.font.size = Pt(10.5)
    bullet.paragraph_format.left_indent = Inches(0.24)
    bullet.paragraph_format.first_line_indent = Inches(-0.14)
    bullet.paragraph_format.space_after = Pt(3)
    bullet.paragraph_format.line_spacing = 1.05

    header = sec.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    rr = header.add_run("STOCKFÁCIL PC  |  DESARROLLO DE APLICACIONES MÓVILES I")
    rr.font.name = "Aptos"
    rr.font.size = Pt(8)
    rr.font.bold = True
    rr.font.color.rgb = RGBColor.from_string(MUTED)

    footer = sec.footer.paragraphs[0]
    rr = footer.add_run("Avance del informe  ·  11 de octubre de 2026")
    rr.font.name = "Aptos"
    rr.font.size = Pt(9)
    rr.font.color.rgb = RGBColor.from_string(MUTED)
    # Add the page number in a right-aligned tab at the end of the footer.
    footer.paragraph_format.tab_stops.add_tab_stop(Inches(6.75))
    footer.add_run("\t")
    set_page_number(footer)


def main():
    make_chart()
    doc = Document()
    style_document(doc)

    p = doc.add_paragraph(style="Title")
    p.add_run("StockFácil PC")
    title_ppr = p._p.get_or_add_pPr()
    title_border = title_ppr.find(qn("w:pBdr"))
    if title_border is not None:
        title_ppr.remove(title_border)
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("Informe del proyecto según las métricas académicas")
    r.font.name = "Aptos"
    r.font.size = Pt(13)
    r.font.color.rgb = RGBColor.from_string(BLUE)
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(10)
    r = p.add_run("Desarrollo de Aplicaciones Móviles I  ·  Avance al 11 de octubre de 2026")
    r.font.size = Pt(9.5)
    r.font.color.rgb = RGBColor.from_string(MUTED)

    add_section_heading(doc, "5.1 Resumen")
    add_body(doc, "StockFácil PC es un proyecto aplicado al control del inventario de una pequeña tienda de artículos de computación. Reúne en una aplicación móvil y un portal administrativo la consulta de productos, categorías y movimientos de stock. En una prueba realizada por la red local, un producto registrado desde el teléfono apareció en el portal, lo que confirmó el intercambio de información entre ambos medios.")
    add_body(doc, "El informe presenta el diagnóstico social, económico, político y tecnológico que orienta la propuesta; los objetivos del proyecto; su justificación; el funcionamiento y alcance; y los productos y documentos que conforman el avance. La solución busca que la persona encargada encuentre con claridad qué artículos hay disponibles y que registre sus ingresos y salidas en el mismo inventario.")
    add_body(doc, "El avance todavía requiere conectar el almacenamiento SQLite del teléfono con las pantallas de la aplicación y publicar el servicio y la base de datos en un alojamiento aprobado para el curso. Las cifras nacionales contextualizan el proyecto; no se han medido ahorros ni tiempos de atención en una tienda específica.")

    add_section_heading(doc, "5.2 Introducción")
    add_body(doc, "El control del inventario requiere consultar productos, conocer sus cantidades disponibles y registrar cada ingreso o salida. StockFácil PC reúne esas funciones en una aplicación móvil y un portal administrativo que muestran la información del inventario desde un mismo servicio.")
    add_body(doc, "El proyecto se desarrolla para una pequeña tienda de artículos de computación. La aplicación móvil permite consultar y mantener productos, elegir categorías y registrar ingresos o salidas de stock. El portal presenta un resumen del inventario y permite administrar productos, categorías y movimientos. La prueba de comunicación entre teléfono y portal se realizó en una red local; el uso en un alojamiento público forma parte de los entregables pendientes.")
    add_body(doc, "En las secciones siguientes se describen el contexto del proyecto, sus objetivos SMART, los beneficios que persigue, sus límites y los productos que se encuentran disponibles o pendientes de completar. El diagnóstico se apoya en información pública nacional y en fuentes oficiales o institucionales; sirve para explicar el entorno y no sustituye la validación de una tienda identificada.")

    add_section_heading(doc, "5.3 Diagnóstico del entorno")
    diagnosis_intro = add_body(doc, "El diagnóstico revisa cuatro aspectos que ayudan a explicar por qué una herramienta móvil y administrativa puede ser útil para organizar un inventario: social, económico, político y tecnológico.")
    diagnosis_intro.paragraph_format.keep_with_next = True

    h = doc.add_paragraph(style="Heading 2")
    h.add_run("5.3.1 Aspecto social")
    social_body = add_body(doc, "El uso del teléfono móvil forma parte de las actividades cotidianas en el país. En el cuarto trimestre de 2025, el 89,2 % de las personas usuarias de Internet de seis años o más se conectó mediante un celular; además, el 95,7 % de los hogares contaba con telefonía móvil. Esta presencia respalda que StockFácil PC incluya una opción móvil para consultar productos durante las labores de inventario. Las cifras describen a la población peruana y no a los trabajadores de una tienda en particular.")
    social_body.paragraph_format.keep_with_next = True
    add_source_line(doc, "Fuente directa: ", [("INEI, Estadísticas de las TIC en los hogares, IV trimestre de 2025", URL_INEI)])

    h = doc.add_paragraph(style="Heading 2")
    h.add_run("5.3.2 Aspecto económico")
    add_body(doc, "Las micro, pequeñas y medianas empresas tienen una presencia amplia en la economía peruana. PRODUCE registró 2 331 173 Mipyme formales en 2024, equivalentes al 99,3 % de las empresas formales. Dentro de ese grupo, comercio y servicios concentraron la mayor parte de las empresas. Este contexto da relevancia a herramientas sencillas para ordenar tareas diarias de una tienda, como revisar existencias y anotar entradas y salidas.")
    add_body(doc, "StockFácil PC organiza la consulta de artículos y el registro de movimientos con el objetivo de apoyar las decisiones de atención y reposición. El avance no declara un ahorro monetario ni una reducción de tiempos medidos; esos resultados deben compararse con datos de una tienda real. La figura resume la concentración de Mipyme por actividad económica y no representa ventas ni resultados de StockFácil PC.")
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    p.add_run().add_picture(str(CHART), width=Inches(6.55))
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("Figura 1. Mipyme formales por sector principal en 2024")
    r.bold = True
    r.font.size = Pt(9.5)
    p = doc.add_paragraph(style="Source Line")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run("Fuente: elaboración propia con datos de PRODUCE. ")
    add_hyperlink(p, "Consultar el informe Las Mipyme en Cifras 2024", URL_PRODUCE_BOOK)
    add_source_line(doc, "Fuentes directas: ", [("PRODUCE, cifras de las Mipyme al cierre de 2024", URL_PRODUCE_PAGE), ("Informe Las Mipyme en Cifras 2024", URL_PRODUCE_BOOK)])

    h = doc.add_paragraph(style="Heading 2")
    h.add_run("5.3.3 Aspecto político")
    policy_body = add_body(doc, "La Política Nacional de Transformación Digital al 2030, aprobada por el Decreto Supremo N.° 085-2023-PCM, establece una orientación nacional para ampliar el uso de servicios y herramientas digitales. La norma incluye al sector privado en cuanto le resulta aplicable. Para StockFácil PC, este marco aporta contexto a la propuesta de ordenar información de inventario mediante una aplicación y un portal.")
    policy_body.paragraph_format.keep_with_next = True
    political_scope = add_body(doc, "El alcance documentado registra productos, categorías y movimientos de stock; no contempla datos identificatorios de clientes ni trabajadores. Por ello, el informe cita la normativa de protección de datos como referencia del entorno peruano, sin afirmar que el proyecto gestione información personal ni que esta cita certifique cumplimiento legal.")
    political_scope.paragraph_format.keep_with_next = True
    political_sources = add_source_line(doc, "Fuentes directas: ", [("PCM, Decreto Supremo N.° 085-2023-PCM", URL_PCM), ("Autoridad Nacional de Protección de Datos Personales, Decreto Supremo N.° 016-2024-JUS", URL_ANPD)])
    political_sources.paragraph_format.keep_with_next = True

    h = doc.add_paragraph(style="Heading 2")
    h.add_run("5.3.4 Aspecto tecnológico")
    add_body(doc, "Las herramientas digitales permiten consultar información desde distintos dispositivos y compartirla entre el teléfono y la computadora. PRODUCE reportó que, en la encuesta correspondiente a 2023, el 14,3 % de las Mipyme declaró usar alguna plataforma digital de mercado para concretar ventas. Ese indicador mide canales de venta por Internet, no el uso de sistemas para controlar inventarios; se incorpora únicamente como referencia sobre la adopción digital empresarial.")
    add_body(doc, "StockFácil PC utiliza una aplicación Android, un portal web y un servicio que comparte los datos entre ambos. Android documenta SQLite como una alternativa para guardar información estructurada dentro del dispositivo. En el código del proyecto ya existe la estructura inicial de la base SQLite móvil, pero las pantallas todavía consultan y modifican los datos por medio del servicio que funciona en la computadora. Por eso, el trabajo sin conexión aún no está integrado. La prueba vigente se limita a la red local.")
    add_body(doc, "El informe de tendencias empresariales de IBM para 2026 ofrece una referencia internacional sobre la evolución de la tecnología en las empresas. Sus resultados son globales y no describen específicamente a las tiendas peruanas; la decisión de usar una aplicación móvil y un portal responde a las funciones requeridas por este proyecto.")
    add_source_line(doc, "Fuentes directas: ", [("PRODUCE, Las Mipyme en Cifras 2024", URL_PRODUCE_BOOK), ("Android Developers, guardar datos con SQLite", URL_ANDROID), ("IBM Institute for Business Value, tendencias empresariales y tecnológicas 2026", URL_IBM)])

    add_section_heading(doc, "5.4 Objetivos")
    add_body(doc, "Los objetivos siguen el enfoque SMART: S, específicos; M, medibles; A, alcanzables; R, relevantes; y T, temporales, con un plazo definido. Se comprobarán mediante funciones demostrables y documentos revisables; no se asignan porcentajes de ahorro sin una medición de referencia.")
    add_objective(doc, "Objetivo 1. Completar el registro local en el teléfono", "Integrar la base SQLite móvil con las pantallas de StockFácil PC para registrar, buscar, editar y eliminar productos, y conservar los cambios al cerrar y volver a abrir la aplicación, antes de la entrega final del curso.", "realizar las cuatro operaciones y comprobar que un producto guardado permanece disponible después de reiniciar la aplicación en un dispositivo Android.")
    add_objective(doc, "Objetivo 2. Publicar y comprobar el servicio compartido", "Alojar la base de datos y el servicio web en una opción gratuita aprobada por el docente, y comprobar antes de la entrega final que la aplicación móvil y el portal pueden consultar y actualizar el mismo producto y sus movimientos.", "registrar un producto y un ingreso o salida desde el teléfono, y confirmar que los datos coinciden en el portal usando el servicio alojado.")
    add_objective(doc, "Objetivo 3. Completar la documentación funcional", "Entregar, antes de la presentación final, los modelos de datos, el diagrama entidad-relación, los casos de uso, la descripción de funciones, los flujos de la aplicación, las reglas de negocio y los mockups.", "revisar que cada documento solicitado esté incluido y que describa las funciones presentes en la aplicación y el portal.")

    add_section_heading(doc, "5.5 Justificación del proyecto")
    add_body(doc, "StockFácil PC reúne la consulta de productos y el registro de movimientos en el teléfono y el portal. La prueba en red local mostró el mismo producto en ambos.")
    add_body(doc, "Desde el punto de vista técnico, el proyecto conecta la aplicación móvil y el portal por medio de un servicio común. La base de datos SQLite utilizada por el servicio está en la computadora durante el avance; la base SQLite del teléfono todavía debe integrarse con las pantallas. Completar ambos puntos permitirá acercar la solución a las métricas del curso.")
    add_body(doc, "Desde el punto de vista económico y organizacional, disponer de una sola consulta de productos y movimientos busca facilitar el trabajo de quien atiende el inventario y apoyar decisiones sobre disponibilidad y reposición. El informe no atribuye al proyecto ahorros, ventas adicionales ni reducción comprobada de errores porque aún no se ha medido con una tienda identificada.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Beneficiarios")
    add_bullet(doc, "Directos: la persona encargada de registrar productos y movimientos; quien administra o supervisa el inventario; y el estudiante que desarrolla y documenta la solución.", "Directos:")
    add_bullet(doc, "Indirectos: las personas que consultan la disponibilidad de artículos y reciben una respuesta basada en el inventario registrado.", "Indirectos:")

    add_section_heading(doc, "5.6 Definición y alcance")
    add_body(doc, "StockFácil PC organiza el inventario de artículos de computación mediante tres componentes: la aplicación Android para el uso móvil, el portal web para administración y un servicio web que comunica ambos. El modelo de datos considera categorías, productos y movimientos de stock. Una categoría puede agrupar varios productos y cada producto puede tener varios movimientos registrados.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Funciones incluidas")
    add_bullet(doc, "Consultar y buscar productos por nombre o código; registrar productos y editar sus datos.")
    add_bullet(doc, "Organizar productos por categoría y revisar la cantidad disponible.")
    add_bullet(doc, "Registrar ingresos y salidas de stock, con una nota o motivo cuando corresponda.")
    add_bullet(doc, "Consultar desde el portal un resumen del inventario, las categorías y los movimientos registrados.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Límites del avance")
    add_bullet(doc, "La integración entre el teléfono y el portal se ha comprobado en la red local de la computadora.")
    add_bullet(doc, "La base SQLite que comparte los datos está en la computadora donde se ejecuta el servicio. La base SQLite del teléfono está creada como estructura inicial, pero su conexión con las pantallas y la sincronización siguen pendientes.")
    add_bullet(doc, "El servicio y la base de datos aún no están publicados en Internet. El proyecto se concentra en inventario y no incluye ventas, facturación, pagos ni gestión de clientes.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Documentación que sustenta el proyecto")
    add_bullet(doc, "Modelo lógico y modelo físico de la base de datos, junto con el diagrama entidad-relación.")
    add_bullet(doc, "Casos de uso, descripción funcional, flujos de la aplicación y reglas de negocio.")
    add_bullet(doc, "Mockups de las pantallas móviles y del portal administrativo.")
    add_bullet(doc, "Registro de las comprobaciones que muestran la comunicación entre la aplicación y el portal.")

    add_section_heading(doc, "5.7 Productos y entregables")
    add_body(doc, "El avance incluye productos funcionales que pueden demostrarse y documentos que deben completarse para la entrega final. Se distingue el estado actual para que la presentación describa lo que ya está disponible y lo que forma parte del cierre del proyecto.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Productos disponibles en el avance")
    add_bullet(doc, "Aplicación Android StockFácil PC con lista y búsqueda de productos, formularios de mantenimiento y registro de movimientos de stock.")
    add_bullet(doc, "Portal administrativo con resumen del inventario y secciones para productos, categorías y movimientos.")
    add_bullet(doc, "Servicio web y base de datos SQLite en la computadora para compartir la información entre la aplicación y el portal durante las pruebas locales.")
    add_bullet(doc, "Comprobación en red local: un producto agregado desde el teléfono aparece en el portal. La aplicación también cuenta con productos ficticios para su demostración.")
    p = doc.add_paragraph(style="Heading 2")
    p.add_run("Entregables para completar")
    add_bullet(doc, "Conectar y comprobar la base SQLite del teléfono con las pantallas, incluida la conservación local de productos y la sincronización con el servicio.")
    add_bullet(doc, "Publicar el servicio y la base de datos en el alojamiento gratuito aprobado por el docente y verificar el acceso desde el teléfono y el portal.")
    add_bullet(doc, "Entregar los modelos de base de datos, el diagrama entidad-relación, los casos de uso, las funciones, los flujos, las reglas de negocio y los mockups.")
    add_bullet(doc, "Grabar un video Demo Reel estructurado de tres a cinco minutos. El video complementa la sustentación y no la reemplaza.")

    # Set document properties and enable widow/orphan control for all body paragraphs.
    doc.core_properties.title = "StockFácil PC Informe del proyecto según las métricas académicas"
    doc.core_properties.subject = "Avance del informe hasta productos y entregables"
    doc.core_properties.keywords = "StockFácil PC, inventario, Android, SQLite, métricas del proyecto"
    for paragraph in doc.paragraphs:
        paragraph.paragraph_format.widow_control = True
    doc.save(OUT)
    print(f"DOCX={OUT}")
    print(f"CHART={CHART}")


if __name__ == "__main__":
    main()
