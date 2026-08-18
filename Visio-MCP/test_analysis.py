"""
Test analysis script to verify critical issues in visio_server.py
"""
import re

def analyze_code():
    with open('visio_server.py', 'r') as f:
        code = f.read()
    
    issues = []
    
    # Issue 1: Check error handling pattern
    print("=" * 80)
    print("ISSUE 1: String-based error handling")
    print("=" * 80)
    error_checks = re.findall(r'if "Error" in (\w+):', code)
    print(f"Found {len(error_checks)} string-based error checks")
    print("This pattern is fragile - any success message containing 'Error' will be treated as failure")
    print("Example: 'Error-Free Diagram created' would trigger error handling\n")
    
    # Issue 2: COM object lifecycle
    print("=" * 80)
    print("ISSUE 2: COM object cleanup")
    print("=" * 80)
    # Check if documents are properly cleaned up on exceptions
    doc_assignments = re.findall(r'open_documents\[.*?\]\s*=', code)
    doc_deletions = re.findall(r'del open_documents\[', code)
    print(f"Document assignments: {len(doc_assignments)}")
    print(f"Document deletions: {len(doc_deletions)}")
    print("If an exception occurs after assignment, COM objects may leak\n")
    
    # Issue 3: Active page assumption
    print("=" * 80)
    print("ISSUE 3: ActivePage assumptions")
    print("=" * 80)
    active_page_usage = re.findall(r'app\.ActivePage', code)
    print(f"Found {len(active_page_usage)} uses of app.ActivePage")
    print("Problem: ActivePage can be None or wrong page if user switches documents")
    print("Should use: doc.Pages[1] or doc.ActivePage with validation\n")
    
    # Issue 4: ConnectorToolDataObject
    print("=" * 80)
    print("ISSUE 4: ConnectorToolDataObject usage")
    print("=" * 80)
    connector_usage = code.count('ConnectorToolDataObject')
    print(f"Found {connector_usage} uses of ConnectorToolDataObject")
    print("Problem: This is a property that only exists when connector tool is active")
    print("It will fail at runtime with 'object has no attribute' error")
    print("Should use: page.DrawLine() or proper stencil masters\n")
    
    # Issue 5: Async sleep
    print("=" * 80)
    print("ISSUE 5: Blocking sleep in async functions")
    print("=" * 80)
    sleeps = re.findall(r'time\.sleep\((\d+)\)', code)
    print(f"Found {len(sleeps)} time.sleep calls: {sleeps}")
    print("Problem: time.sleep blocks the async event loop")
    print("Should use: await asyncio.sleep() or remove if unnecessary\n")
    
    # Issue 6: Path handling
    print("=" * 80)
    print("ISSUE 6: File path normalization")
    print("=" * 80)
    path_keys = re.findall(r'open_documents\[(.*?)\]', code)
    print(f"Found {len(path_keys)} open_documents accesses")
    print("Problem: Paths used as dict keys but not normalized")
    print("Same file with different path formats won't match:")
    print("  - 'C:\\\\Users\\\\file.vsdx' vs 'C:/Users/file.vsdx'")
    print("  - Relative vs absolute paths")
    print("Should use: os.path.normpath() or os.path.abspath()\n")
    
    # Issue 7: Document state verification
    print("=" * 80)
    print("ISSUE 7: Stale document references")
    print("=" * 80)
    print("Only 'open_visio_file' checks if doc reference is valid (line 165)")
    print("Other functions don't verify the COM object is still valid")
    print("If Visio crashes or user closes doc, COM calls will fail\n")

if __name__ == "__main__":
    analyze_code()
