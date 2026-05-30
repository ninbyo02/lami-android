# Indentation Review

Input code:

```python
def add(x, y):
    # 2つの数を加算する
    return x + y

def divide(x, y):
    if y == 0:
        return "エラー"
    return x / y
```

Sanitized output preserves:

- four-space function bodies
- nested branch indentation
- blank line between functions
- code-line leading spaces and tabs

Classification after implementation: `indentation_preserved_in_code_block`.
