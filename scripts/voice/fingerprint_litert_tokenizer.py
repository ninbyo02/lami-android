"""Fingerprint bounded tokenizer sections using LiteRT-LM's header schema (section kinds 4/6)."""
import argparse
import hashlib
import json
import struct
from pathlib import Path


class Header:
    def __init__(self, data):
        self.data=data
    def number(self, fmt, offset):
        if offset is None or offset<0 or offset+struct.calcsize(fmt)>len(self.data):
            raise ValueError('Header offset outside bounded metadata')
        return struct.unpack_from(fmt,self.data,offset)[0]
    def field(self, table, index):
        vtable=table-self.number('<i',table)
        size=self.number('<H',vtable)
        if size<4:raise ValueError('Invalid vtable')
        if 4+2*index>=size:return None
        relative=self.number('<H',vtable+4+2*index)
        return table+relative if relative else None
    def pointer(self, offset):
        target=offset+self.number('<I',offset)
        self.number('<I',target)
        return target


def fingerprint(path):
    before=path.stat()
    with path.open('rb') as stream:
        fixed=stream.read(32)
        if len(fixed)!=32 or fixed[:8]!=b'LITERTLM':raise ValueError('Invalid LiteRT-LM header')
        end=struct.unpack_from('<Q',fixed,24)[0]
        if not 36<=end<=min(before.st_size,2*1024*1024):raise ValueError('Invalid metadata size')
        h=Header(fixed+stream.read(end-32))
        if len(h.data)!=end:raise ValueError('Truncated metadata')
        root=32+h.number('<I',32)
        section_metadata=h.pointer(h.field(root,1))
        vector=h.pointer(h.field(section_metadata,0))
        count=h.number('<I',vector)
        if count>128:raise ValueError('Too many sections')
        sections=[]
        for index in range(count):
            table=h.pointer(vector+4+4*index)
            kind_field=h.field(table,3)
            kind=h.number('<B',kind_field) if kind_field else 0
            start=h.number('<Q',h.field(table,1));stop=h.number('<Q',h.field(table,2))
            if not end<=start<=stop<=before.st_size:raise ValueError('Invalid section range')
            if kind in [4,6]:
                size=stop-start
                if not 0<size<=16*1024*1024:raise ValueError('Tokenizer section exceeds bound')
                stream.seek(start);data=stream.read(size)
                if len(data)!=size:raise ValueError('Truncated tokenizer section')
                sections.append({'kind_id':kind,'bytes':size,'sha256':hashlib.sha256(data).hexdigest()})
    after=path.stat()
    if (before.st_size,before.st_mtime_ns)!=(after.st_size,after.st_mtime_ns):raise ValueError('Model changed while reading')
    if not sections:raise ValueError('No known tokenizer section')
    return {'model':str(path),'model_bytes':before.st_size,'tokenizer_sections':sections,
            'scope':'Tokenizer bytes only; does not establish weight, template, runtime or numerical equivalence'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('models',nargs='+',type=Path);p.add_argument('--output',required=True,type=Path)
    a=p.parse_args();report={'validation':'failed'}
    try:
        results=[fingerprint(path) for path in a.models]
        report.update(validation='passed',models=results,
            tokenizer_sections_identical=all(x['tokenizer_sections']==results[0]['tokenizer_sections'] for x in results))
    except (OSError,ValueError,struct.error,TypeError) as error:report['error']=str(error)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2));return 0 if report['validation']=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
