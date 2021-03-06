<#assign archiveId = document.getMetadataValue("archive")>
<#assign name = document.toString()>
<#assign waId = document.getMetadataValue('wa-id')!"">
<#assign title>${name?html}<#if waId?has_content> &ndash; ${waId?html}</#if></#assign>
<#assign imageLinkBase>${cp}/document/imagelink/${document.source?replace('faust://xml/document/', '')}</#assign>
<#assign css>
	#transcript { margin: 1em auto; text-align: center }
	
	.disabled { color: #ccc }
	
	#transcript-navigation { text-align: center; font-size: 85%; margin: 1em }
	#transcript-document { margin: 1em; padding: 1em; border: 1px solid #ccc }
	#transcript-placeholder { text-align: center; color: #ccc }
	
	#transcript-browser { border: 1px solid #ccc;  background: #000; color: #ccc }
	#transcript-browser .yui3-widget-hd { padding: 0.5em 1em }
	#transcript-browser .yui3-widget-hd a { color: #ccc; }	
	#transcript-browser .yui3-widget-bd { white-space: nowrap; }
	#transcript-browser .yui3-widget-bd li { width: 150px; text-align: center }
	#transcript {height: 800px}
	#transcript-facsimile, #transcript-text {height: 100%}
	#transcript-text { overflow: auto }
	#transcript-swf { border: 1px inset grey; }
	.zone { border: 1px dashed #ccc; margin: 3em 0; padding: 1em}	
</#assign>
<#assign header>
	<link rel="stylesheet" type="text/css" href="${cp}/static/js/imageviewer/css/iip.css" />


	<script type="text/javascript" src="${cp}/static/js/dragsvg.js"></script>
	<script type="text/javascript" src="${cp}/static/js/goddag.js"></script>
	<script type="text/javascript" src="${cp}/static/js/innersvg.js"></script>
	<script type="text/javascript"> var imageLinkBase = "${imageLinkBase}" </script>

</#assign>
<@faust.page title=title css=css header=header layout="wide">
	<div class="demo-note">
		<p>Diese Ansicht zeigt die diplomatische Umschrift einer Beispielhandschrift, wie sie automatisch aus kodierten Quellen abgeleitet wird.</p>
		
		<p>Der Umschrift mangelt es zwar noch an Genauigkeit; demgegenüber kann man hier jedoch bereits die Navigation in mehrseitigen Handschriften sowie die Nutzung verschiedener Darstellungsmodi ausprobieren.</p>
		
		<p>
		Nutzen Sie bitte die Navigationsleiste im oberen Bereich der Umschrift, um das Manuskript durchzublättern, entweder seitenweise oder im Überblick.
		Für die Überblicksdarstellung klicken Sie bitten auf „Blättern” und ziehen Sie ihre Maus über die Seitenleiste, um größere Bereiche der Handschrift zu überfliegen.
		Ein Doppelklick auf eine verkleinerte Seitendarstellung springt zur entsprechenden Umschrift. 
		</p>
	</div>
	<#include "document-contents.ftl"/>
</@faust.page>