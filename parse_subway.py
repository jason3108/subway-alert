#!/usr/bin/env python3
import json

with open('/tmp/beijing_subway.json', 'r') as f:
    data = json.load(f)

# Line name mapping (to simplify names like "1号线八通线" -> "1号线")
line_mappings = {
    "1号线八通线": "1号线",
    "2号线": "2号线",
    "4号线": "4号线",
    "4号线大兴线": "4号线",
    "5号线": "5号线",
    "6号线": "6号线",
    "7号线": "7号线",
    "8号线": "8号线",
    "9号线": "9号线",
    "10号线": "10号线",
    "11号线": "11号线",
    "12号线": "12号线",
    "13号线": "13号线",
    "14号线": "14号线",
    "15号线": "15号线",
    "16号线": "16号线",
    "17号线": "17号线",
    "19号线": "19号线",
    "昌平线": "昌平线",
    "大兴线": "大兴线",
    "房山线": "房山线",
    "燕房线": "燕房线",
    "顺义线": "顺义线",
    "S1线": "S1线",
    "八通线": "1号线",  # Merged with 1号线
    "机场线": "首都机场线",
    "大兴机场线": "大兴机场线",
}

lines = {}

for line in data['l']:
    raw_name = line['ln']
    line_name = line_mappings.get(raw_name, raw_name)
    
    if line_name not in lines:
        lines[line_name] = []
    
    for station in line['st']:
        name = station['n']
        # sl is "lng,lat" format (高德坐标)
        coords = station['sl'].split(',')
        if len(coords) == 2:
            lng = float(coords[0])
            lat = float(coords[1])
            # Check if station already exists
            exists = any(s['name'] == name for s in lines[line_name])
            if not exists:
                lines[line_name].append({
                    'name': name,
                    'lat': lat,
                    'lng': lng
                })

# Sort lines by logical order
line_order = [
    "1号线", "2号线", "4号线", "5号线", "6号线", "7号线", "8号线", "9号线", "10号线",
    "11号线", "12号线", "13号线", "14号线", "15号线", "16号线", "17号线", "19号线",
    "昌平线", "大兴线", "房山线", "燕房线", "顺义线", "S1线", "首都机场线", "大兴机场线"
]

print(f"Total lines: {len(lines)}")
total_stations = 0
for line_name in line_order:
    if line_name in lines:
        print(f"\n// {line_name} ({len(lines[line_name])} stations)")
        total_stations += len(lines[line_name])
        for station in lines[line_name]:
            print(f'        PresetStation("{station["name"]}", "{line_name}", {station["lat"]}, {station["lng"]}),')

print(f"\n// Total: {total_stations} stations")
