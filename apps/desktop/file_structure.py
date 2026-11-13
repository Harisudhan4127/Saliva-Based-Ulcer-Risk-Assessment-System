from rich.tree import Tree
from rich.console import Console
from pathlib import Path

def build_rich_tree(path, tree):
    # Filter out hidden folders to keep the tree clean
    for entry in sorted(path.iterdir(), key=lambda x: (x.is_file(), x.name.lower())):
        if entry.name.startswith(('.', '__')): 
            continue
            
        if entry.is_dir():
            branch = tree.add(f"📁 {entry.name}")
            build_rich_tree(entry, branch)
        else:
            tree.add(f"📄 {entry.name}")

# 1. Initialize Rich Console with recording enabled
console = Console(record=True)
root_path = Path(".")
rich_tree = Tree(f"{root_path.absolute()}")

# 2. Build the tree
build_rich_tree(root_path, rich_tree)

# 3. Print to console and then export to file
console.print(rich_tree)
console.save_text("project_structure.txt")

print("\nSuccess: Tree structure saved to project_structure.txt")
