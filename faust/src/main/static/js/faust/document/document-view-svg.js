SVG_NS = "http://www.w3.org/2000/svg";
DRAG_NS = "http://www.codedread.com/dragsvg";

YUI.add('document-view-svg', function (Y) {		

	// START of view components. Keep in sync with model.
	// These components are based on SVG. The model is only
	// accessing a narrow interface to learn about the actual
	// size, so in theory this could be exchanged with say a 
	// HTML / CSS Transforms implementation

	Faust.ViewComponent.prototype.getCoord = function(coordRotation) {
		var matrix = this.view.viewportElement.createSVGMatrix();
		matrix = matrix.rotate(coordRotation);
		return Faust.SVG.boundingBox(this.view, matrix).x;
	};

	Faust.ViewComponent.prototype.getExt = function(coordRotation) {
		var matrix = this.view.viewportElement.createSVGMatrix();
		matrix = matrix.rotate(coordRotation);
		var result = Faust.SVG.boundingBox(this.view, matrix).width;
		return result;
	};
	
	Faust.ViewComponent.prototype.setCoord = function(coord, coordRotation) {
		//var ctm = this.view.getCTM();
		//var ctmTransform = this.view.viewportElement.createSVGTransformFromMatrix(ctm);
		//var ctmTransformInv = this.view.viewportElement.createSVGTransformFromMatrix(ctm.inverse());
		
		var myRot = this.globalRotation();
		var myRotMx = this.view.viewportElement.createSVGMatrix().rotate(myRot);
		var myRotTf = this.view.viewportElement.createSVGTransformFromMatrix(myRotMx);
		var myRotTfInv = this.view.viewportElement.createSVGTransformFromMatrix(myRotMx.inverse());
		
		var matrix = this.view.viewportElement.createSVGMatrix();
		var currentCoord = this.getCoord(coordRotation);
		var deltaCoord = coord - currentCoord;
		
		matrix = matrix.rotate(coordRotation);
		matrix = matrix.translate(deltaCoord, 0);
		matrix = matrix.rotate(-coordRotation);
		var transform = this.view.viewportElement.createSVGTransformFromMatrix(matrix);
		this.view.transform.baseVal.consolidate();
		this.view.transform.baseVal.appendItem(myRotTfInv);
		this.view.transform.baseVal.appendItem(transform);
		this.view.transform.baseVal.appendItem(myRotTf);
		this.view.transform.baseVal.consolidate();
	};

	Faust.ViewComponent.prototype.rotate = function(deg) {
		var matrix = this.view.viewportElement.createSVGMatrix();
		matrix = matrix.rotate(deg);
		var transform = this.view.viewportElement.createSVGTransformFromMatrix(matrix);
		this.view.transform.baseVal.insertItemBefore(transform, 0);		
	};
	
	Faust.ViewComponent.prototype.svgContainer = function() {
		if (this.parent)
			if (this.parent.view)
				return this.parent.view;
			else
				return this.parent.svgContainer();
		else
			if (this.svgCont)
				return this.svgCont;
		else
			throw ('Cannot find SVG container for view component');
	};
	
	Faust.ViewComponent.prototype.svgDocument = function() {
		return document;
	};

	Faust.ViewComponent.prototype.onRelayout = function() {
		if (this.view) {
			//this.view.setAttribute("x", this.x.toString());
			//this.view.setAttribute("y", this.y.toString());
			//this.view.setAttribute("transform", "translate(" + this.x.toString(), + "," + this.y.toString + ")");
			//var translate = this.view.transform.baseVal.getItem(0);
			//translate.setTranslate(this.x, this.y);
			//this.setX(this.x);
			//this.setY(this.y);
			//this.view.transform.baseVal.replaceItem(0);
		}
	};
	
	Faust.ViewComponent.prototype.render = function() {
		this.view = this.createView();
		this.svgContainer().appendChild(this.view);
		this.rotate(this.rotation);
		Y.each(this.children, function(c) { c.render(); });
	};

	Faust.ViewComponent.prototype.measure = function() {
		var bbox = this.view.getBBox();
		return { width: Math.round(bbox.width), height: Math.round(bbox.height)};		
	};
	
	Y.augment (Faust.DefaultVC, Faust.ViewComponent);
	
	Faust.BreakingVC.prototype.createView = function() {
		var view = this.svgDocument().createElementNS(SVG_NS, "g");
		view.setAttribute('class', 'BreakingVC');
		return view;
	};

	Y.augment (Faust.BreakingVC, Faust.ViewComponent);
	
	Faust.VSpace.prototype.createView = function() {
		var result = this.svgDocument().createElementNS(SVG_NS, "rect");
		result.setAttribute('class', 'VSpace');
		result.setAttribute('width', '0.1em');
		result.setAttribute('style', 'visibility: hidden;');
		//TODO dynamically calculate from context line height
		var height = String(this.vSpaceHeight * 2) + 'em';
		result.setAttribute('height', height);
		return result;
	};
	
	Y.augment(Faust.VSpace, Faust.BreakingVC);

	Faust.HSpace.prototype.createView = function() {
		var result = this.svgDocument().createElementNS(SVG_NS, "rect");
		result.setAttribute('class', 'HSpace');
		result.setAttribute('height', '0.1em');
		result.setAttribute('style', 'visibility: hidden;');
		//TODO dynamically calculate from context ? 
		var width = String(this.hSpaceWidth * 0.5) + 'em';
		result.setAttribute('width', width);
		return result;
	};
	
	Y.augment(Faust.HSpace, Faust.ViewComponent);

	
	Faust.DefaultVC.prototype.createView = function() {
		var view = this.svgDocument().createElementNS(SVG_NS, "g");
		view.setAttribute('class', 'DefaultVC');
		return view;
	};
	
	
	
	//Faust.DefaultVC.prototype.render = function() {
		//this.view = createView();
		//Y.each(this.children, function(c) { c.render(); });
	//};
	
	Y.augment(Faust.Line, Faust.ViewComponent);


	
	Faust.Surface.prototype.createView = function() {
		var surface = this.svgDocument().createElementNS(SVG_NS, "g");
		surface.setAttribute('class', 'Surface');
		return surface;
		
	};
	
	Y.augment (Faust.Surface, Faust.ViewComponent);

	Faust.Zone.prototype.createView = function() {
		var svgContainer = this.svgContainer();
		var result = this.svgDocument().createElementNS(SVG_NS, "g");
		var box0 = this.svgDocument().createElementNS(SVG_NS, 'rect');
		box0.setAttribute('width', '0.1em');
		box0.setAttribute('height', '0.1em');
		box0.setAttribute('x', '0');
		box0.setAttribute('y', '0');
		box0.setAttribute('style', 'fill: transparent; stroke: black; visibility: hidden')
		result.appendChild(box0);
		result.setAttribute('class', 'Zone');
		result.setAttributeNS(DRAG_NS, 'drag:enable', 'true');
		
			return result;
	};
	Y.augment(Faust.Zone, Faust.ViewComponent);

	
	Faust.Line.prototype.createView = function() {
		var line = this.svgDocument().createElementNS(SVG_NS, "g");
		line.setAttribute('class', 'Line');
		return line;
	};
	Y.augment(Faust.Line, Faust.ViewComponent);

	Faust.Text.prototype.createView = function() {
		var text = this.svgDocument().createElementNS(SVG_NS, "text");
		this.setStyles(text);
		text.appendChild(this.svgDocument().createTextNode(this.text));
		return text;
	};
	
	Faust.Text.prototype.onRelayout = function() {
		if (this.view) {
			//this.view.setAttribute("x", this.x.toString());
			//this.view.setAttribute("y", this.y.toString());
			//this.setX(this.x);
			//this.setY(this.y);
		}
		
		if (this.strikethrough) {
			this.strikethrough.setAttribute("x1", this.x);
			this.strikethrough.setAttribute("x2", this.x + this.width);
			this.strikethrough.setAttribute("y1", this.y - this.measure().height / 6);
			this.strikethrough.setAttribute("y2", this.y - this.measure().height / 6);
			this.strikethrough.setAttribute("stroke", "#333");
			//this.strikethrough.transform.baseVal = this.view.transform.baseVal;
			this.strikethrough.transform.baseVal.initialize(this.view.transform.baseVal.consolidate());
		}
		
		if (this.underline) {
			this.underline.setAttribute("x1", this.x);
			this.underline.setAttribute("x2", this.x + this.width);
			this.underline.setAttribute("y1", this.y);
			this.underline.setAttribute("y2", this.y);
			this.underline.setAttribute("stroke", this.handColor());
			this.underline.transform.baseVal.initialize(this.view.transform.baseVal.consolidate());
		}
	};


	Faust.Text.prototype.render = function() {
		this.view = this.createView();
		this.svgContainer().appendChild(this.view);
		var textBox = this.view.getBBox();
		if ("strikethrough" in this.textAttrs) {
			this.strikethrough = this.svgDocument().createElementNS(SVG_NS, "line");
			this.svgContainer().appendChild(this.strikethrough);
		}
		if ("underline" in this.textAttrs) {
			this.underline = this.svgDocument().createElementNS(SVG_NS, "line");
			this.svgContainer().appendChild(this.underline);
		}
		this.rotate(this.rotation);	
		Y.each(this.children, function(c) { c.render(); });		
	};
	Y.augment(Faust.Text, Faust.ViewComponent);

	Faust.GrLine.prototype.createView = function() {
		this.zoneSpanning = this.svgDocument().createElementNS(SVG_NS, 'rect');
		this.zoneSpanning.setAttribute('fill', 'url(#curlyLinePattern)');
		this.zoneSpanning.setAttribute('width', '100');
		this.svgContainer().appendChild(this.zoneSpanning);
		
		var block = this.svgDocument().createElementNS(SVG_NS, 'rect');
		block.setAttribute('width', '0.1em');
		block.setAttribute('height', '0.1em');
		block.setAttribute('style', 'fill: transparent; stroke: black; visibility: hidden')
		return block;
		
		
	};
	
	Faust.GrLine.prototype.onRelayout = function() {
		this.zoneSpanning.setAttribute('x', 0);
		this.zoneSpanning.setAttribute('y', 0);
		
		
		this.zoneSpanning.setAttribute('height', this.parent.getExt(this.parent.rotY()));
	};
	
	Y.augment(Faust.GrLine, Faust.ViewComponent);
	

	
	Faust.GLine.prototype.createView = function() {
		var line = this.svgDocument().createElementNS(SVG_NS, "line");
		line.setAttribute("x1", this.x);
		line.setAttribute("y1", this.y - 10);
		line.setAttribute("x2", this.x + 40);
		line.setAttribute("y2", this.y - 10);
		line.setAttribute("stroke-width", 1);
		line.setAttribute("stroke", "black");
		return line;
	};
	Y.augment(Faust.GLine, Faust.ViewComponent);

	Faust.GBrace.prototype.createView = function() {
		var path = this.svgDocument().createElementNS(SVG_NS, "path");
		path.setAttribute("d", "M " + (this.x) + " " + (this.y) + " q 5,-10 20,-5 q 5,0 10,-10 q -5,0 10,10 q 10,-5 20,5");
		path.setAttribute("stroke-width", 1);
		path.setAttribute("stroke", "black");
		path.setAttribute("fill", "transparent");
		return path;
	};
	Y.augment(Faust.GBrace, Faust.ViewComponent);
	
	// END of view components
		
	// SVG Helper Methods
	
	Faust.SVG = function(){};
	
	/** 
	 * Return the bounding box of element as seen from the coordinate system given by matrix.
	 */
	Faust.SVG.boundingBox = function(element, matrix) {
		  
		// macro to create an SVGPoint object
		  function createPoint (x, y) {
		    var point = element.viewportElement.createSVGPoint();
		    point.x = x;
		    point.y = y;
		    return point;
		  }

		  // macro to create an SVGRect object
		  function createRect (x, y, width, height) {
		    var rect = element.viewportElement.createSVGRect();
		    rect.x = x;
		    rect.y = y;
		    rect.width = width;
		    rect.height = height;
		    return rect; 
		  }

		  // local bounding box in local coordinates
		  var box = element.getBBox();

		  
		  
		  var inv = matrix.inverse()
		  
		  inv = inv.multiply(element.getCTM());
		  
		  // create an array of SVGPoints for each corner
		  // of the bounding box and update their location
		  // with the transform matrix 
		  var corners = [];
		  var point = createPoint(box.x, box.y);
		  corners.push(point.matrixTransform(inv) );
		  point.x = box.x + box.width;
		  point.y = box.y;
		  corners.push( point.matrixTransform(inv) );
		  point.x = box.x + box.width;
		  point.y = box.y + box.height;
		  corners.push( point.matrixTransform(inv) );
		  point.x = box.x;
		  point.y = box.y + box.height;
		  corners.push( point.matrixTransform(inv) );
		  var max = createPoint(corners[0].x, corners[0].y);
		  var min = createPoint(corners[0].x, corners[0].y);

		  // identify the new corner coordinates of the
		  // fully transformed bounding box
		  for (var i = 1; i < corners.length; i++) {
		    var x = corners[i].x;
		    var y = corners[i].y;
		    if (x < min.x) {
		      min.x = x;
		    }
		    else if (x > max.x) {
		      max.x = x;
		    }
		    if (y < min.y) {
		      min.y = y;
		    }
		    else if (y > max.y) {
		      max.y = y;
		    }
		  }
		  
		  // return the bounding box as an SVGRect object
		  var result = createRect(min.x, min.y, max.x - min.x, max.y - min.y);
		  return result;
	};
	
}, '0.0', {
	requires: ["node", "dom", "event", "document-model"]
});