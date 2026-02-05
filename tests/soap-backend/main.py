from flask import Flask, Response, request
from pathlib import Path
import sys


app = Flask(__name__)

@app.route("/diplomdaten", methods=["POST", "GET"])
def diplomdaten():
    return Response(Path("./responses/diplomdaten.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")


@app.route("/testservice", methods=["POST", "GET"])
def testservice():

    variant_header = request.headers.get("X-Variant")

    print(f"FOUND VARIANT HEADER: {variant_header}", file=sys.stderr)


    if variant_header == "INVALID_CONTENT_TYPE":
        return Response("{\"hello\":\"world\"}", mimetype="application/json; charset=utf-8")
    
    if variant_header == "INVALID_RESPONSE":
        return Response("<foo>bar</foo>", mimetype="text/xml; charset=utf-8")


    return Response(Path("./responses/testservice.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")

@app.route("/oneshared", methods=["POST", "GET"])
def oneshared_service():
    return Response(Path("./responses/oneshared.xml").read_text(encoding="utf-8"), mimetype="text/xml; charset=utf-8")

print(app.url_map)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080, debug=True)

