import anthropic
import sys
import os

def generate_code(prompt):
    """Generate code using Claude API"""
    
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise ValueError("ANTHROPIC_API_KEY not set")
    
    client = anthropic.Anthropic(api_key=api_key)
    
    message = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=4096,
        messages=[{
            "role": "user",
            "content": prompt
        }]
    )
    
    return message.content[0].text

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: claude_client.py <prompt>")
        sys.exit(1)
    
    prompt = sys.argv[1]
    code = generate_code(prompt)
    print(code)
