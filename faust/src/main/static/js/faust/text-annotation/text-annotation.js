YUI.add('text-annotation', function (Y) {

    var Range = function (start, end) {
        this.start = start;
        this.end = end;
    };
    var Name = function (namespace, localName) {
        this.namespace = namespace;
        this.localName = localName;
    };
    var Text = function (id, type, contentLength, range, content) {
        this.id = id;
        this.type = type;
        this.contentLength = contentLength;
        this.range = range || new Range(0, contentLength);
        this.content = content || "";
    };
    var TextTarget = function (text, range) {
        this.text = text;
        this.range = range;
    };

    var UNKNOWN_NAME = new Name(null, "");
    var Annotation = function (name, data, targets) {
        this.name = name || UNKNOWN_NAME;
        this.data = data || {};
        this.targets = targets || [];
    };
    Y.extend(Annotation, Object, {
        target: function () {
            return (this.targets ? this.targets[0] : null);
        },
        targetIn: function (text) {
            return Y.Array.find(this.targets, function (t) {
                return (t.text === text);
            });
        }
    });

    Y.extend(TextTarget, Object, {
        textContent: function () {
            return this.range.of(this.text.content);
        }
    });

    Y.extend(Text, Object, {
        partition: function () {
            var offsets = [];
            Y.Array.each(this.annotations, function (a) {
                Y.Array.each(a.targets, function (t) {
                    if (t.text == this) {
                        var range = t.range;
                        if (offsets.indexOf(range.start) < 0) offsets.push(range.start);
                        if (offsets.indexOf(range.end) < 0) offsets.push(range.end);
                    }
                }, this);
            }, this);
            offsets.sort(function (a, b) {
                return a - b;
            });

            if (offsets.length == 0 || offsets[0] > this.range.start) offsets.unshift(this.range.start);
            if (offsets.length == 1 || offsets[offsets.length - 1] < this.range.end) offsets.push(this.range.end);

            var partitions = [];
            var start = -1;
            Y.Array.each(offsets, function (end) {
                if (start >= 0) partitions.push(new Range(start, end));
                start = end;
            });
            return partitions;
        },
        find: function (start, end, filter) {
            var result = [],
                nameFilter = null,
                range =  new Range(
                    (Y.Lang.isNumber(start) ? start : 0),
                    (Y.Lang.isNumber(end) ? end : this.contentLength)
                );

            if (Y.Lang.isFunction(filter)) {
                nameFilter = filter;
            } else if (Y.Lang.isArray(filter)) {
                nameFilter = function (a) {
                    return (Y.Array.indexOf(filter, a.name.localName) >= 0);
                };
            } else if (Y.Lang.isString(filter)) {
                nameFilter = function (a) {
                    return filter == a.name.localName;
                };
            } else {
                nameFilter = function () {
                    return true;
                }
            }

            this._searchRange(this.rangeIndex._root, range, nameFilter, result);
            return result;
        },
        _searchRange: function (node, range, annotationFilter, result) {
            // Don't search nodes that don't exist
            if (node === null) {
                return;
            }

            // If range is to the right of the rightmost point of any interval
            // in this node and all children, there won't be any matches.
            if (range.start > node.maxEnd) {
                return;
            }

            // Search left children
            if (node.left !== null) {
                this._searchRange(node.left, range, annotationFilter, result);
            }

            // Check this node
            if (node.key.overlapsWith(range) || range.includes(node.key)) {
                Y.Array.each(Y.Array.filter(node.values, annotationFilter), function (v) {
                    result.push(v);
                });
            }

            // If range is to the left of the start of this interval,
            // then it can't be in any child to the right.
            if (range.end < node.key.start) {
                return;
            }

            // Otherwise, search right children
            if (node.right !== null) {
                this._searchRange(node.right, range, annotationFilter, result);
            }
        },
        _setupRangeIndexNode: function (node) {
            if (node === null) {
                return 0;
            } else {
                node.maxEnd = Math.max(node.key.end, this._setupRangeIndexNode(node.left), this._setupRangeIndexNode(node.right));
                return node.maxEnd;
            }
        }
    }, {
        create: function (data) {
            var text = new Text(data.text.id, data.text.t, data.text.l, data.textRange, data.textContent), names = {};
            Y.Object.each(data.names, function (v, k) {
                names[k] = new Name(v[0], v[1]);
            });
            text.rangeIndex = new Y.Faust.RBTree(Range.sort, function (a) {
                return a.targetIn(text).range;
            });
            text.localNameIndex = {};
            text.annotations = Y.Array.map(data.annotations, function (a) {
                var annotation = new Annotation(names[a.n], a.d, Y.Array.map(a.t, function (target) {
                    return new TextTarget((target[2] == text.id ? text : target[2]), new Range(target[0], target[1]));
                }));

                text.rangeIndex.insert(annotation);

                var ln = annotation.name.localName;
                text.localNameIndex[ln] = (text.localNameIndex[ln] || []).concat(annotation);

                return annotation;
            });
            text._setupRangeIndexNode(text.rangeIndex._root);

            return text;
        }
    });
    Y.extend(Name, Object, {
        toString: function () {
            return (this.namespace == null ? "" : "{" + this.namespace + "}") + this.localName;
        },
        fromString: function (str) {
            var firstBrace = str.indexOf("{");
            if (firstBrace < 0) {
                return new NS.Name(null, str);
            }
            var secondBrace = str.indexOf("}");
            if (secondBrace < firstBrace || secondBrace >= (str.length - 1)) {
                Y.error("Invalid Name", str, { throwFail: true });
            }

            return new NS.Name(str.substring(firstBrace + 1, secondBrace), str.substring(secondBrace + 1))
        }
    });
    Y.extend(Range, Object, {
        length: function () {
            return (this.end - this.start);
        },
        precedes: function (other) {
            return (this.end <= other.start);
        },
        overlapsWith: function (other) {
            return ((this.start < other.end) && (this.end > other.start));
        },
        includes: function (other) {
            return (this.start <= other.start && this.end >= other.end);
        },
        of: function (text) {
            return text.substring(this.start, this.end);
        },
        equalsStartOf: function (other) {
            return  (this.start == other.start) && (this.end == other.start);
        },
        amountOfOverlapWith: function (other) {
            return (Math.min(this.end, other.end) - Math.max(this.start, other.start));
        },
        fromId: function (str) {
            var components = str.replace("r", "").split("-");
            return new Range(parseInt(components[0]), parseInt(components[1]));
        },
        toId: function () {
            return "r" + this.start.toString() + "-" + this.end.toString();
        },
        toString: function () {
            return "[" + this.start + ", " + this.end + "]";
        }
    }, {
        sort: function (ra, rb) {
            return (ra.start == rb.start ? (rb.end - ra.end) : (ra.start - rb.start));
        }
    });

    Y.mix(Y.namespace("Faust"), {
        Text: Text,
        Name: Name,
        Range: Range,
        TextTarget: TextTarget,
        Annotation: Annotation
    });
}, '0.0', {
    requires: ["text-index", "base", "substitute", "array-extras", "io", "json"]
});